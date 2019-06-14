//Required settings
'use strict';

const serverVersion = "0.4.2";
//Libraries
const http = require('http');
const sense = require('sense-energy-node');
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
This process gets data from Sense in two methods:
1.) Websocket: gets current device power details
2.) API calls: used to get device list, Sense monitor info, and recent Timeline history events

We open and close the websocket once a minute to get a snapshot of current usage. Holding the websocket open for longer periods causes
load on Sense servers and can trigger rate limits, which impact usability of the Sense mobile app. We sent an update to the SmartThings
hub via LAN after processing each websocket data payload.

Note that POSTing data to the hub via LAN is subject to strict character limits. Because of this, we split device lists into chunks
to make sure the size of each payload is small.

The API calls run once every 5 minutes. This data is less time-sensitive and just needs to be run to refresh the device list and
current monitor data. We also check the timeline history to see if there were any short-lived on/off events that happened between 1-minute
websocket checks. Each of these calls then POSTs an update to the hub.

***************************************************************************************************************************************/

var websocketPollingInterval = 60;  //Number of seconds between opening/closing the websocket
var refreshInterval = 300;  //Number of seconds between updating monitor data via API calls (not needed as frequently)
var monitorRefreshScheduler;

//Global Variables
var mySense;                        //Main Sense API object
var currentlyProcessing = false;    //Flag to ensure only one websocket packet is handled at a time
var deviceList = {};                //Holds list of Sense devices
var monitorData = {};               //Holds monitor device data
var deviceIdList = [];              //Holds list of Sense devices (just the IDs) - used to look for stale devices in ST
var serviceStartTime = Date.now();  //Returns time in millis
var eventCount = 0;                 //Keeps a tally of how many times data was sent to ST in the running sessions
var postOptions = {                 //Basic template for POSTing to SmartThings
        method: 'POST',
        uri: 'http://' + smartThingsHubIP + ':39500/event',
        headers: { 'source': 'STSense' },
        json: true
}

var lastPush = new Date();
lastPush.setDate(lastPush.getDate() - 1);

//Main startup function - gets initial data and sets up recurring tasks
async function startSense(){
    try {
        mySense = await sense({email: email, password: password, verbose: false})   //Set up Sense API object and authenticate

        //Get devices
        await mySense.getDevices().then(devices => {
            for (let dev of devices) {
                addDevice(dev);
            }
        });

        //Get monitor info
        await mySense.getMonitorInfo()
        .then(monitor => {
            monitorData = monitor;
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
                mySense.closeStream();
                if (currentlyProcessing){
                    return 0;
                }
                currentlyProcessing = true;
                tsLogger(`Fresh websocket device data incoming...`)
                processData(data);
            }
            return 0;
        });

        //Handle closures and errors
        mySense.events.on('close', (data) => {
            tsLogger(`Sense WebSocket Closed | Reason: ${data.wasClean ? 'Normal' : data.reason}`);
            let interval = websocketPollingInterval && websocketPollingInterval > 10 ? websocketPollingInterval : 60;
            setTimeout(() => {
                mySense.openStream();
            },  interval * 1000);
        });
        mySense.events.on('error', (data) => {
            tsLogger('Error: Sense WebSocket Closed | Reason: ' + data.msg);
        });

        //Open websocket flow (and re-open again on an interval)
        mySense.openStream();

        //Set up schedules
        //scheduleWebsocketPoll();
        scheduleMonitorRefresh();

    } catch (error) {
        tsLogger(`FATAL ERROR: ${error}`);
        if (error.stack){
            tsLogger(`FATAL ERROR: ${error.stack}`);
        }
        process.exit();
    }
}

function scheduleMonitorRefresh(){
    //Wait 30 seconds (so this offset from the websocket intervals), then set up recurring refresh
    let interval = refreshInterval && refreshInterval > 10 ? refreshInterval : 300;
    setTimeout(() => {
        monitorRefreshScheduler = setInterval(() => {
            periodicRefresh();
        }, interval * 1000);
    }, 30000);
}

//Add a device to our local device list
function addDevice(data) {
    try {
        if (data.id === "SenseMonitor" || data.id === 'solar') {   //The monitor device itself is treated differently
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
                name: (isGuess ? data.name.trim() + ' (?)' : data.name.trim()),
                state: "unknown",
                usage: -1,
                currentlyOn: false,
                recentlyChanged: true,
                lastOn: new Date().getTime()
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
            deviceIdList.push(data.id);
        }
    } catch (error) {
        tsLogger(error.stack);
    }

}

//Handles periodic refresh tasks that run less frequently
function periodicRefresh(){
    tsLogger('Refreshing monitor data, monitor data, and missed events...');
    mySense.getMonitorInfo()
        .then(monitor => {
            monitorData = monitor;
            updateMonitorInfo();
        })

    refreshDeviceList();
    getMissedEvents();
}

//Refresh device list from Sense and send a list of the ID's to ST to check for stale devices
function refreshDeviceList(){
    mySense.getDevices().then(devices => {
        for (let dev of devices) {
            if (!deviceList[dev.id]) {
                addDevice(dev);
            }
            else{
                deviceList[dev.id]= dev;
            }
        }
    }).then(() => {
        if (deviceIdList.length > 0){
            let options = postOptions;
            options.body = {"deviceIds": JSON.stringify(deviceIdList)}
            request(options);
        }
    });
}

//Checks Sense Timeline for short-lived on/off events that may have been missed; send any missed events to ST
function getMissedEvents(){
    try {
        let missedToggles = [];
        mySense.getTimeline().then(timeline => {
            timeline.items.map(event => {
                if (event.type == "DeviceWasOn" && Date.parse(event.start_time) > deviceList[event.device_id].lastOn){
                    tsLogger(`Missed toggle event detected for ${event.device_name} (${event.device_id})`);
                    missedToggles.push(event.device_id);
                    deviceList[event.device_id].lastOn = new Date().getTime();
                }
            })

            //Post missed events to SmartThings so we can toggle those on and off
            if (missedToggles.length > 0){
                let options = postOptions;
                postOptions.body = {"toggleIds": missedToggles}
                request(options)
            }
        })

    } catch (error) {
        tsLogger(error.stack);
    }
}

//Update the monitor device info
function updateMonitorInfo(otherData = {}) {
    try{
        let monitor = monitorData;
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
        tsLogger(error + error.stack);
    }
}

//Main websocket data processing
function processData(data) {
    try {
        if (data.payload && data.payload.devices) {

            //Mark off saved list so we can detect which have been seen lately
            Object.keys(deviceList).forEach(function(key) {
                if (key !== "SenseMonitor") {
                    deviceList[key].currentlyOn = false;
                    if (deviceList[key].usage !== -1) {
                        deviceList[key].recentlyChanged = false;
                    }
                }
            });

            //Loop over currently active devices and refresh the saved list
            for (const dev of data.payload.devices) {

                if (!deviceList[dev.id]) { //If Device is NEW make a new spot for it in the deviceMap
                    addDevice(dev);
                }

                //If device still failed to add for some reason, bail out of this entry
                if (!deviceList[dev.id]){
                    continue;
                }

                let prevState = deviceList[dev.id].state;

                //Don't go below 1 watt
                if (convUsage(deviceList[dev.id].usage) < 1) {
                    deviceList[dev.id].usage = convUsage(1);
                }

                if (dev.name !== "Other") {
                    if (prevState !== "on" && prevState !== "unknown") {
                        tsLogger('Device State Changed: ' + dev.name + " turned ON!");
                        deviceList[dev.id].recentlyChanged = true;
                        deviceList[dev.id].lastOn = new Date();
                    }
                }

                //Get other device info
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

            //Push device data into overall Sense Monitor device
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
            updateMonitorInfo(otherMonData);


            let devArray = [];  //We'll convert object list to array for easier parsing in ST

            //Loop over saved list again and mark any remaining devices as off
            Object.keys(deviceList).forEach(function(key) {
                if (key !== "SenseMonitor") {
                    if (deviceList[key].currentlyOn === false) {
                        if (deviceList[key].name !== "Other" && deviceList[key].state !== 'off' && deviceList[key].state !== "unknown") {
                            tsLogger('Device State Changed: ' + deviceList[key].name + " turned OFF!");
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

            lastPush = new Date();  //Take note of the last ST push timestamp

            //Split device into smaller chunks to make sure we don't exceed SmartThings limits
            let deviceGroups = [];
            let chunkSize = 5;
            for (var index = 0; index < devArray.length; index += chunkSize){
                let deviceGroup = devArray.slice(index, index+chunkSize);
                deviceGroups.push(deviceGroup);
            }

            let options = postOptions;
            options.body =
            {
                'devices': deviceGroups[0],
                'timestamp': Date.now(),
                'serviceInfo': {
                    'version': serverVersion,
                    'sessionEvts': eventCount,
                    'startupDt': getServiceUptime(),
                    'ip': getIPAddress(),
                    'port': callbackPort,
                    'config': {
                        'refreshInterval': refreshInterval,
                        'websocketPollingInterval': websocketPollingInterval,
                        'smartThingsHubIP': smartThingsHubIP
                    }
                },
                'totalUsage': data.payload.w,
                'frameId': data.payload.frame
            }

            if (smartThingsAppId !== undefined || smartThingsAppId !== '') {
                options.headers.senseAppId = config.smartThingsAppId;
            }

            //****Send to SmartThings!****
            //Send first group that also contains config info
            request(options)
            .then(function() {
                eventCount++;
                tsLogger('** Sent (' + devArray.length + ') Devices to SmartThings! | Usage: (' + convUsage(data.payload.w) + 'W) **');

                //Push other groups (if applicable)
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
    } catch (error) {
        tsLogger(error + ' ' + error.stack);
        currentlyProcessing = false;
    }

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

//Format usage
function convUsage(val, rndTo = 2) {
    if (val !== -1) {
        val = parseFloat(val).toFixed(rndTo);
    }
    return val;
}

//Logging with timestamp
function tsLogger(msg) {
    let dt = new Date().toLocaleString();
    console.log(dt + ' | ' + msg);
}


//Handle process closing
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

//Handle graceful shutdown
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


//Get uptime
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
}

//Get Node server's IP so SmartThings hub can keep track of it
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

function resetMonitorRefreshSchedule(){
    clearInterval(monitorRefreshScheduler);
    scheduleMonitorRefresh();
}

function startWebServer() {
    app.set('port', callbackPort);
    appServer.listen(callbackPort, function() {
        tsLogger('Sense Monitor Service (v' + serverVersion + ') is Running at (IP: ' + getIPAddress() + ' | Port: ' + callbackPort + ') | ProcessId: ' + process.pid);
    });
    app.post('/updateSettings', function(req, res) {
        try {
            tsLogger('** Settings Update Received from SmartThings **');
            if (req.headers.websocketpollinginterval !== undefined && parseInt(req.headers.websocketpollinginterval) !== websocketPollingInterval) {
                tsLogger('++ Changed Setting (websocketPollingInterval) | New Value: (' + req.headers.websocketpollinginterval + ') | Old Value: (' + websocketPollingInterval + ') ++');
                websocketPollingInterval = parseInt(req.headers.websocketpollinginterval);
            }
            if (req.headers.refreshinterval !== undefined && parseInt(req.headers.refreshinterval) !== refreshInterval) {
                tsLogger('++ Changed Setting (refreshInterval) | New Value: (' + req.headers.refreshinterval + ') | Old Value: (' + refreshInterval + ') ++');
                refreshInterval = parseInt(req.headers.refreshinterval);
                resetMonitorRefreshSchedule();
            }
        } catch (error) {
            tsLogger(error + error.stack);
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
