//Required settings
'use strict';

const serverVersion = "0.3.0";
//Libraries
const http = require('http');
const sense = require('unofficial-sense'); //Temporarily using our own version until pull requests are merged in
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
var autoReconnect = config.autoReconnect || true;
var usagePushThreshold = 200; //Change in usage that determines when a special push to ST is made
var maxSecBetweenPush = 60; //Maximum number of seconds between data pushes to ST
var minSecBetweenPush = 10; //Minimum number of seconds between data pushes to ST

//Global Variables
var reconnectPending = false;
var deviceList = {};
var deviceFilter = [];
var serviceStartTime = Date.now(); //Returns time in millis
var eventCount = 0; //Keeps a tally of how many times data was sent to ST in the running sessions
const arrSum = arr => arr.reduce((a, b) => Math.abs(a) + Math.abs(b), 0);

var lastPush = new Date();
lastPush.setDate(lastPush.getDate() - 1);

function tsLogger(msg) {
    let dt = new Date().toLocaleString();
    console.log(dt + ' | ' + msg);
}

function addDevice(data) {
    tsLogger("Adding New Device: (" + data.name + ") to DevicesList...");
    if (data.id === "SenseMonitor") {
        deviceList[data.id] = data;
    } else {
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

function startSenseStream() {
    //On initial load, get basic list of devices
    sense({
            email: email,
            password: password,
            verbose: false
        })
        .then((mySense) => {
                tsLogger("Successfully Authenticated with Sense! Data is Incoming!");
                reconnectPending = false;
                getSenseDevices();
                updateMonitorInfo();

                //Handle websocket data updates
                mySense.events.on('data', processData);

                //Handle closures and errors
                mySense.events.on('close', (data) => {
                    handleWSClose({
                        eventData: data,
                        msg: 'Connection closed.'
                    });
                });
                mySense.events.on('error', (data) => {
                    handleWSClose({
                        eventData: data,
                        msg: 'An error occurred. ' + data
                    });
                });

                function getSenseDevices() {
                    mySense.getDevices().then(devices => {
                        // console.log("devices:", devices);
                        for (let dev of devices) {
                            addDevice(dev);
                        }
                    });
                }

                function updateMonitorInfo(usageVal = 0, otherData = {}) {
                    mySense.getMonitorInfo().then(monitor => {
                        // console.log('MonitorInfo:', monitor);
                        let devData = {
                            id: "SenseMonitor",
                            name: "Sense Monitor",
                            state: "on",
                            usage: usageVal,
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
                    });
                }

                function handleWSClose(data) {
                    console.log('Sense WebSocket Closed | Reason: ' + data.msg);
                    mySense.events.removeListener('data', processData);
                    mySense.events.removeListener('close', handleWSClose);
                    mySense.events.removeListener('error', handleWSClose);
                    reconnect();
                }

                //Reconnect if option enabled and if a reconnect attempt is not already in progress
                function reconnect() {
                    if (autoReconnect && !reconnectPending) {
                        reconnectPending = true;
                        console.log("Reconnecting...");
                        startSenseStream();
                    }
                }

                function convUsage(val, rndTo = 2) {
                    if (val !== -1) {
                        val = parseFloat(val).toFixed(rndTo);
                    }
                    return val;
                }

                //Process the data from Sense!
                function processData(data) {
                    if (data.type === "realtime_update") {
                        if (data.payload && data.payload.devices) {
                            let updateNow = false;
                            // console.log('Payload:', data.payload);

                            //Mark off saved list so we can detect which have been seen lately
                            Object.keys(deviceList).forEach(function(key) {
                                deviceList[key].currentlyOn = false;
                                if (deviceList[key].usage !== -1) {
                                    deviceList[key].recentlyChanged = false;
                                } //Reset recentChange flag unless it's initial load
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
                                        tsLogger(dev.name + " turned on!");
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
                                        deviceList[dev.id].deviceType = dev.tags.type;
                                    }
                                }
                            }
                            //Convert list to array for easier parsing in ST
                            let devArray = [];
                            let totalUseArr = [];
                            //Loop over saved list again and mark any remaining devices as off
                            Object.keys(deviceList).forEach(function(key) {
                                if (key !== "SenseMonitor") {
                                    totalUseArr.push(convUsage(deviceList[key].usage));
                                    if (deviceList[key].currentlyOn === false) {
                                        if (deviceList[key].name !== "Other" && deviceList[key].state !== 'off' && deviceList[key].state !== "unknown") {
                                            tsLogger(deviceList[key].name + " turned off!");
                                            updateNow = true;
                                            deviceList[key].recentlyChanged = true;
                                        }
                                        deviceList[key].state = "off";
                                        deviceList[key].usage = 0;
                                    }
                                }
                                devArray.push(deviceList[key]);
                            });
                            // console.log('devArray: ', devArray);

                            let secondsSinceLastPush = (Date.now() - lastPush.getTime()) / 1000;
                            // console.log('lastPush: ', secondsSinceLastPush, ' minSecs: ', minSecBetweenPush);

                            //Override updateNow if it's been less than 15 seconds since our last push
                            if (secondsSinceLastPush <= minSecBetweenPush) {
                                updateNow = false;
                            }

                            if (updateNow || secondsSinceLastPush >= maxSecBetweenPush) {
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
                                console.log('total usage: ' + arrSum(totalUseArr) + ' | payload usage: ' + data.payload.w);
                                updateMonitorInfo(convUsage(data.payload.w) || 0, otherMonData);
                                lastPush = new Date();
                                let options = {
                                    method: 'POST',
                                    uri: 'http://' + smartThingsHubIP + ':39500/event',
                                    headers: {
                                        'source': 'STSense'
                                    },
                                    body: {
                                        'devices': devArray,
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
                                        }
                                    },
                                    json: true
                                };

                                if (smartThingsAppId !== undefined || smartThingsAppId !== '') {
                                    options.headers.senseAppId = config.smartThingsAppId;
                                }
                                //Send to SmartThings!
                                request(options)
                                    .then(function() {
                                        eventCount++;
                                        tsLogger('**Sent Data for (' + devArray.length + ') Devices to SmartThings Hub Successfully! | Last Updated: (' + secondsSinceLastPush + 'sec)**');
                                    })
                                    .catch(function(err) {
                                        console.log("ERROR: Unable to connect to SmartThings Hub: " + err.message);
                                    });
                            }
                        }
                    }
                }
            },
            function(err) {
                console.log("Sense error: " + err.message);
                return;
            });
}


function startWebServer() {
    app.set('port', callbackPort);
    appServer.listen(callbackPort, function() {
        tsLogger('Sense Monitor Service (v' + serverVersion + ') is Running at (IP: ' + getIPAddress() + ' | Port: ' + callbackPort + ') | ProcessId: ' + process.pid);
    });
    app.post('/updateSettings', function(req, res) {
        tsLogger('SmartThings Sent a Setting Update... | PID: ' + process.pid);
        if (req.headers.minsecbetweenpush !== undefined && parseInt(req.headers.minsecbetweenpush) !== minSecBetweenPush) {
            tsLogger('updateSetting | minSecBetweenPush | new: ' + req.headers.minsecbetweenpush + ' | old: ' + minSecBetweenPush);
            minSecBetweenPush = parseInt(req.headers.minsecbetweenpush);
        }
        if (req.headers.maxsecbetweenpush !== undefined && parseInt(req.headers.maxsecbetweenpush) !== maxSecBetweenPush) {
            tsLogger('updateSetting | maxSecBetweenPush | new: ' + req.headers.maxsecbetweenpush + ' | old: ' + maxSecBetweenPush);
            maxSecBetweenPush = parseInt(req.headers.maxsecbetweenpush);
        }
        if (req.headers.usagepushthreshold !== undefined && parseInt(req.headers.usagepushthreshold) !== usagePushThreshold) {
            tsLogger('updateSetting | usagePushThreshold | new: ' + req.headers.usagepushthreshold + ' | old: ' + usagePushThreshold);
            usagePushThreshold = parseInt(req.headers.usagepushthreshold);
        }
        if (req.headers.smartthingshubip !== undefined && req.headers.smartthingshubip !== smartThingsHubIP) {
            tsLogger('updateSetting | smartThingsHubIP | new: ' + req.headers.smartthingshubip + ' | old: ' + smartThingsHubIP);
            smartThingsHubIP = req.headers.smartthingshubip;
        }
    });
    process.stdin.resume(); //so the program will not close instantly
    //do something when app is closing
    process.on('exit', exitHandler.bind(null, {
        exit: true
    }));
}

// This starts the Stream and Webserver
startSenseStream();
startWebServer();