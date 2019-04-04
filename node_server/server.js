//Required settings
'use strict';

const serverVersion = "0.3.0";
//Libraries
const http = require('http');
const sense = require('./unofficial-sense'); //Temporarily using our own version until pull requests are merged in
const request = require("request-promise");
const express = require('express');
const os = require('os');
const app = express();
const appServer = http.createServer(app);

//Required Setting (Set in Config.js)
const config = require('./config');
const email = config.email;
const password = config.password;
var smartThingsHubIP = config.smartThingsHubIP;
const smartThingsAppId = config.smartThingsAppId || undefined;
const callbackPort = config.callbackPort || 9021;

//Optional Settings (Set in Config.js)
/**************************************************************************************************************************************
    This process opens up a websocket connection to Sense; we see realtime data from Sense every couple seconds.
    While parsing that data, we watch for significant changes so we can push to SmartThings only when relevant to avoid spamming the hub.
    At baseline, we send an update at least once every minute (maxSecBetweenPush).
    We do NOT send updates less than 10 seconds apart (minSecBetweenPush).
    We send an update when at least one device has turned on or off.
    We send an update when power usage on a device has changed by at least 200 watts (usagePushThreshold).
***************************************************************************************************************************************/

var websocketPollingInterval = 60;  //Number of seconds between opening/closing the websocket
var refreshInterval = 300;  //Number of seconds between updating monitor data via API calls (not needed as frequently)
var usagePushThreshold = 200; //Change in usage that determines when a special push to ST is made
var maxSecBetweenPush = 60; //Maximum number of seconds between data pushes to ST
var minSecBetweenPush = 10; //Minimum number of seconds between data pushes to ST

//Global Variables
var mySense;
var currentlyProcessing = false;
var deviceList = {};
var serviceStartTime = Date.now(); //Returns time in millis
var eventCount = 0; //Keeps a tally of how many times data was sent to ST in the running sessions

var lastPush = new Date();
lastPush.setDate(lastPush.getDate() - 1);

function tsLogger(msg) {
    let dt = new Date().toLocaleString();
    console.log(dt + ' | ' + msg);
}

function addDevice(data) {
    try {        
        if (data.id === "SenseMonitor") {
            deviceList[data.id] = data;
        } else {
            
            //Ignore devices that are hidden on the Sense app (usually merged devices)
            if (data.tags.DeviceListAllowed == "false"){                
                return 0
            }
            
            tsLogger("Adding New Device: (" + data.name + ") to DevicesList...");        
            let isGuess = (data.tags && data.tags.NameUserGuess && data.tags.NameUserGuess === 'true');
            let devData = {
                id: data.id,
                name: (isGuess ? data.name + ' (?)' : data.name),
                state: "unknown",
                usage: -1,
                currentlyOn: false,
                recentlyChanged: true
            };

            if (data.id !== "SenseMonitor") {
                devData.location = data.location || "";
                devData.make = data.make || "";
                devData.model = data.model || "";
                devData.icon = data.icon || "";
                if (data.tags) {
                    devData.mature = (data.tags.Mature === "true") || false;
                    devData.revoked = (data.tags.Revoked === "true") || false;
                    devData.dateCreated = data.tags.DateCreated || "";
                }
            }
            deviceList[data.id] = devData;
        }
    } catch (error) {
        tsLogger(error.stack);
    }
    
}

function exitHandler(options, err) {
    console.log('exitHandler: (PID: ' + process.pid + ')', options, err);
    if (options.cleanup) {
        tsLogger('exitHandler: ', 'ClosedByUserConsole');
    } else if (err) {
        tsLogger('exitHandler error', err);
        if (options.exit) process.exit(1);
    }
    process.exit();
}

var gracefulStopNoMsg = function() {
    tsLogger('gracefulStopNoMsg: ', process.pid);
    console.log('graceful setting timeout for PID: ' + process.pid);
    setTimeout(function() {
        console.error("Could not close connections in time, forcefully shutting down");
        process.exit(1);
    }, 2 * 1000);
};

var gracefulStop = function() {
    tsLogger('gracefulStop: ', 'ClosedByNodeService ' + process.pid);
    let a = gracefulStopNoMsg();
};

function getIPAddress() {
    var interfaces = os.networkInterfaces();
    for (var devName in interfaces) {
        var iface = interfaces[devName];
        for (var i = 0; i < iface.length; i++) {
            var alias = iface[i];
            if (alias.family === 'IPv4' && alias.address !== '127.0.0.1' && !alias.internal)
                return alias.address;
        }
    }
    return '0.0.0.0';
}

function getServiceUptime() {
    var now = Date.now();
    var diff = (now - serviceStartTime) / 1000;
    //logger.debug("diff: "+ diff);
    return getHostUptimeStr(diff);
}

function getHostUptimeStr(time) {
    var years = Math.floor(time / 31536000);
    time -= years * 31536000;
    var months = Math.floor(time / 31536000);
    time -= months * 2592000;
    var days = Math.floor(time / 86400);
    time -= days * 86400;
    var hours = Math.floor(time / 3600);
    time -= hours * 3600;
    var minutes = Math.floor(time / 60);
    time -= minutes * 60;
    var seconds = parseInt(time % 60, 10);
    return {
        'y': years,
        'mn': months,
        'd': days,
        'h': hours,
        'm': minutes,
        's': seconds
    };
    //return (years + 'y, ' + days + 'd, ' + hours + 'h:' + (minutes < 10 ? '0' + minutes : minutes) + 'm:' + (seconds < 10 ? '0' + seconds : seconds) +'s');
}

//Handles periodic refresh tasks that run less frequently
function periodicRefresh(){
    tsLogger('Refreshing monitor data...');
    mySense.getMonitorInfo()
        .then(monitor => {
            updateMonitorInfo(monitor);
        })
}

//Attempt to refresh auth
function refreshAuth(){
    try {
        mySense.getAuth();    
    } catch (error) {
        tsLogger(`Re-auth failed: ${error}. Exiting.`); 
        process.exit();
    }
    
}

function updateMonitorInfo(monitor, otherData = {}) {
    try{    
        let devData = {
            id: "SenseMonitor",
            name: "Sense Monitor",
            state: "on",
            monitorData: {
                online: (monitor.monitor_info.online === true) || false,
                mac: monitor.monitor_info.mac || "",
                ndt_enabled: (monitor.monitor_info.ndt_enabled === true) || false,
                wifi_signal: monitor.monitor_info.signal || "",
                wifi_ssid: monitor.monitor_info.ssid || "",
                version: monitor.monitor_info.version || ""
            }
        };
        if (Object.keys(otherData).length) {
            for (const key in otherData) {
                devData.monitorData[key] = otherData[key];
            }
        }
        if (monitor.device_detection && monitor.device_detection.in_progress) {
            devData.monitorData.detectionsPending = monitor.device_detection.in_progress || {};
        }
        if (deviceList["SenseMonitor"]) {
            deviceList["SenseMonitor"] = devData;
        } else {
            addDevice(devData);
        }
        
    } catch (error) {
        tsLogger(error);
    }
}

async function startSense(){
    try {
        mySense = await sense({email: email, password: password, verbose: false})
                        
        //Get devices
        await mySense.getDevices().then(devices => {
            // console.log("devices:", devices);
            for (let dev of devices) {
                addDevice(dev);
            }            
        });

        //Get monitor info
        await mySense.getMonitorInfo()
        .then(monitor => {
            updateMonitorInfo(monitor);
        })

        //Set up websocket event handlers
        //Handle websocket data updates (one at a time)
        mySense.events.on('data', (data) => {

            //Check for loss of authorization. If detected, try to reauth
            if (data.payload.authorized == false){
                tsLogger('Authentication failed. Trying to reauth...');
                refreshAuth();
            }

            //Set processing flag so we only send through and process one at a time
            if (data.type === "realtime_update" && data.payload && data.payload.devices) {
                if (currentlyProcessing){
                    return 0;
                }
                currentlyProcessing = true;
                tsLogger(`Fresh data incoming...`)
                processData(data);
            }
            return 0;
        });

        //Handle closures and errors
        mySense.events.on('close', (data) => {
            tsLogger(`Sense WebSocket Closed | Reason: ${data.wasClean ? 'Normal' : data.reason}`);
        });
        mySense.events.on('error', (data) => {            
            tsLogger('Error: Sense WebSocket Closed | Reason: ' + data.msg);            
        });

        //Open websocket flow (and re-open again on an interval)        
        mySense.openStream();
        setInterval(() => {
            //tsLogger(`Opening websocket...`)
            mySense.openStream();
        }, websocketPollingInterval * 1000);

        
        setInterval(() => {
            //tsLogger(`Opening websocket...`)
            periodicRefresh();
        }, refreshInterval * 1000);


    } catch (error) {
        tsLogger(`FATAL ERROR: ${error}`);
        process.exit();        
    }
}

function processData(data) {
    // console.log('Payload:', data);    
    if (data.payload && data.payload.devices) {
        mySense.closeStream();          
        

        //Mark off saved list so we can detect which have been seen lately
        Object.keys(deviceList).forEach(function(key) {
            if (key !== "SenseMonitor") {
                deviceList[key].currentlyOn = false;
                if (deviceList[key].usage !== -1) {
                    deviceList[key].recentlyChanged = false;
                } //Reset recentChange flag unless it's initial load
            }
        });

        //Loop over currently active devices and refresh the saved list
        // We'll send data to SmartThings if status has changed or if usage has changed over a certain amount
        for (const dev of data.payload.devices) {
            //If Device is NEW make a new spot for it in the deviceMap
            if (!deviceList[dev.id]) {
                addDevice(dev);
            }
            // console.log('key(' + dev.id + '):', dev.tags);

            let prevState = deviceList[dev.id].state;
            let prevUsage = convUsage(deviceList[dev.id].usage);
            let currentUsage = convUsage(dev.w);
            let usageDelta = convUsage(currentUsage) - convUsage(prevUsage);

            //Don't go below 1 watt
            if (convUsage(deviceList[dev.id].usage) < 1) {
                deviceList[dev.id].usage = convUsage(1);
            }

            if (dev.name !== "Other") {
                if (prevState !== "on" && prevState !== "unknown") {
                    tsLogger('Device State Changed: ' + dev.name + " turned ON!");
                    updateNow = true;
                    deviceList[dev.id].recentlyChanged = true;
                }
                if (prevUsage !== -1 && Math.abs(usageDelta) > usagePushThreshold) {
                    tsLogger(dev.name + " usage changed by " + usageDelta);
                    updateNow = true;
                }
            }

            deviceList[dev.id].state = "on";
            deviceList[dev.id].usage = convUsage(dev.w);
            deviceList[dev.id].currentlyOn = true;
            if (dev.id !== "SenseMonitor") {
                if (dev.location) {
                    deviceList[dev.id].location = dev.location;
                }
                if (dev.make) {
                    deviceList[dev.id].make = dev.make;
                }
                if (dev.model) {
                    deviceList[dev.id].model = dev.model;
                }
                if (dev.icon) {
                    deviceList[dev.id].icon = dev.icon;
                }
                if (dev.tags) {
                    deviceList[dev.id].mature = (dev.tags.Mature === "true") || false;
                    deviceList[dev.id].revoked = (dev.tags.Revoked === "true") || false;
                    deviceList[dev.id].dateCreated = dev.tags.DateCreated || "";
                    deviceList[dev.id].deviceType = dev.tags.Type;
                    deviceList[dev.id].userDeviceType = dev.tags.UserDeviceType;
                    deviceList[dev.id].isPending = dev.tags.Pending;
                }
            }
        }
        //Convert list to array for easier parsing in ST
        let devArray = [];
        //Loop over saved list again and mark any remaining devices as off
        Object.keys(deviceList).forEach(function(key) {
            if (key !== "SenseMonitor") {
                if (deviceList[key].currentlyOn === false) {
                    if (deviceList[key].name !== "Other" && deviceList[key].state !== 'off' && deviceList[key].state !== "unknown") {
                        tsLogger('Device State Changed: ' + deviceList[key].name + " turned OFF!");
                        updateNow = true;
                        deviceList[key].recentlyChanged = true;
                    }
                    deviceList[key].state = "off";
                    deviceList[key].usage = 0;
                }
            } else {
                deviceList[key].usage = convUsage(data.payload.w);
            }
            devArray.push(deviceList[key]);
        });

        let otherMonData = {};
        if (Object.keys(data.payload.voltage).length) {
            let v = [];
            v.push(convUsage(data.payload.voltage[0], 1));
            v.push(convUsage(data.payload.voltage[1], 1));
            otherMonData.voltage = v;
        }
        if (Object.keys(data.payload.channels).length) {
            let phaseUse = [];
            phaseUse.push(convUsage(data.payload.channels[0], 2));
            phaseUse.push(convUsage(data.payload.channels[1], 2));
            otherMonData.phaseUsage = phaseUse;
        }
        otherMonData.hz = convUsage(data.payload.hz, 0);
        //updateMonitorInfo(otherMonData);
        lastPush = new Date();

        //Split device into smaller chunks to make sure we don't exceed SmartThings limits
        let deviceGroups = [];
        let chunkSize = 5;
        for (var index = 0; index < devArray.length; index += chunkSize){
            let deviceGroup = devArray.slice(index, index+chunkSize);
            deviceGroups.push(deviceGroup);
        }

        let options = {
            method: 'POST',
            uri: 'http://' + smartThingsHubIP + ':39500/event',
            headers: { 'source': 'STSense' },
            body: {
                'devices': deviceGroups[0],
                'timestamp': Date.now(),
                'serviceInfo': {
                    'version': serverVersion,
                    'sessionEvts': eventCount,
                    'startupDt': getServiceUptime(),
                    'ip': getIPAddress(),
                    'port': callbackPort,
                    'config': {
                        'minSecBetweenPush': minSecBetweenPush,
                        'maxSecBetweenPush': maxSecBetweenPush,
                        'usagePushThreshold': usagePushThreshold,
                        'smartThingsHubIP': smartThingsHubIP
                    }
                },
                'totalUsage': data.payload.w,
                'frameId': data.payload.frame
            },
            json: true
        };
        if (smartThingsAppId !== undefined || smartThingsAppId !== '') {
            options.headers.senseAppId = config.smartThingsAppId;
        }
        //Send to SmartThings!
        //tsLogger(`** Preparing to send ${devArray.length} devices to SmartThings! **`);

        request(options)
        .then(function() {
            eventCount++;
            tsLogger('** Sent (' + deviceGroups.length + ') Devices to SmartThings! | Usage: (' + convUsage(data.payload.w) + 'W) **');

            //Push other groups            
            deviceGroups.splice(0,1);
            deviceGroups.forEach(devGroup => {
                options.body = {"devices": devGroup}
                request(options)
            })
            currentlyProcessing = false;
        })
        .catch(function(err) {
            console.log("ERROR: Unable to connect to SmartThings Hub: " + err.message);
            currentlyProcessing = false;
        });
    }
    
}

function convUsage(val, rndTo = 2) {
    if (val !== -1) {
        val = parseFloat(val).toFixed(rndTo);
    }
    return val;
}

//var websocketPollingInterval = 60;  //Number of seconds between opening/closing the websocket
//var refreshInterval = 300;  //Number of seconds between updating monitor data via API calls (not needed as frequently)


function startWebServer() {
    app.set('port', callbackPort);
    appServer.listen(callbackPort, function() {
        tsLogger('Sense Monitor Service (v' + serverVersion + ') is Running at (IP: ' + getIPAddress() + ' | Port: ' + callbackPort + ') | ProcessId: ' + process.pid);
    });
    app.post('/updateSettings', function(req, res) {
        tsLogger('** Settings Update Received from SmartThings **');
        if (req.headers.websocketPollingInterval !== undefined && parseInt(req.headers.websocketPollingInterval) !== websocketPollingInterval) {
            tsLogger('++ Changed Setting (websocketPollingInterval) | New Value: (' + req.headers.websocketPollingInterval + ') | Old Value: (' + websocketPollingInterval + ') ++');
            websocketPollingInterval = parseInt(req.headers.websocketPollingInterval);
        }
        if (req.headers.refreshInterval !== undefined && parseInt(req.headers.refreshInterval) !== refreshInterval) {
            tsLogger('++ Changed Setting (refreshInterval) | New Value: (' + req.headers.refreshInterval + ') | Old Value: (' + refreshInterval + ') ++');
            refreshInterval = parseInt(req.headers.refreshInterval);
        }
    });
    process.stdin.resume(); //so the program will not close instantly
    //do something when app is closing
     process.on('exit', exitHandler.bind(null, {
         exit: true
     }));

    //catches ctrl+c event
    process.on('SIGINT', gracefulStop);

    process.on('SIGUSR2', gracefulStop);

    process.on('SIGHUP', gracefulStop);

    process.on('SIGTERM', gracefulStop);
}

// This starts the Stream and Webserver
startWebServer();
startSense();

