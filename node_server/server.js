//Required settings
'use strict';

const serverVersion = "0.2.0";
//Libraries
const sense = require('unofficial-sense'); //Temporarily using our own version until pull requests are merged in
const request = require("request-promise");

//Required Setting (Set in Config.js)
const config = require('./config');
const email = config.email;
const password = config.password;
const smartThingsHubIP = config.smartThingsHubIP;
const smartThingsAppId = config.smartThingsAppId || undefined;

//Optional Settings (Set in Config.js)
/**************************************************************************************************************************************
    This process opens up a websocket connection to Sense; we see realtime data from Sense every couple seconds.
    While parsing that data, we watch for significant changes so we can push to SmartThings only when relevant to avoid spamming the hub.
    At baseline, we send an update at least once every minute (maximumSecondsBetweenPush).
    We do NOT send updates less than 10 seconds apart (minimumSecondsBetweenPush).
    We send an update when at least one device has turned on or off.
    We send an update when power usage on a device has changed by at least 200 watts (usageThreshold).
***************************************************************************************************************************************/
const autoReconnect = config.autoReconnect || true;
const usageThreshold = config.usageThreshold || 200; //Change in usage that determines when a special push to ST is made
const maximumSecondsBetweenPush = config.maximumSecondsBetweenPush || 60; //Maximum number of seconds between data pushes to ST
const minimumSecondsBetweenPush = config.minimumSecondsBetweenPush || 10; //Minimum number of seconds between data pushes to ST

//Global Variables
var reconnectPending = false;
var deviceList = {};
var lastPush = new Date();
lastPush.setDate(lastPush.getDate() - 1);
// var prevTotalUsage = 0;

function addDevice(data) {
    console.log("Adding New Device: (" + data.name + ") to DevicesList...");
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

function getData() {
    //On initial load, get basic list of devices
    sense({
            email: email,
            password: password,
            verbose: false
        })
        .then((mySense) => {
                console.log("Successfully Authenticated with Sense! Data is Incoming!");
                reconnectPending = false;
                mySense.getDevices().then(devices => {
                    // console.log("devices:", devices);
                    for (let dev of devices) {
                        addDevice(dev);
                    }
                });

                updateMonitorInfo();

                // mySense.getTimeline().then(timeline => {
                //     console.log('TimeLine:', timeline);
                // });

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

                function tsLogger(msg) {
                    let dt = new Date().toLocaleString();
                    console.log(dt + ' | ' + msg);
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
                        getData();
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

                                    if (prevUsage !== -1 && Math.abs(usageDelta) > usageThreshold) {
                                        tsLogger(dev.name + " usage changed by " + usageDelta);
                                        updateNow = true;
                                    }
                                }
                                deviceList[dev.id].state = "on";
                                deviceList[dev.id].usage = convUsage(dev.w);
                                deviceList[dev.id].currentlyOn = true;
                                if (dev.id !== "SenseMonitor") {
                                    deviceList[dev.id].location = dev.location || "";
                                    deviceList[dev.id].make = dev.make || "";
                                    deviceList[dev.id].model = dev.model || "";
                                    deviceList[dev.id].icon = dev.icon || "";
                                    if (dev.tags) {
                                        deviceList[dev.id].mature = (dev.tags.Mature === "true") || false;
                                        deviceList[dev.id].revoked = (dev.tags.Revoked === "true") || false;
                                        deviceList[dev.id].dateCreated = dev.tags.DateCreated || "";
                                    }
                                }
                            }
                            //Convert list to array for easier parsing in ST
                            var devArray = [];

                            //Loop over saved list again and mark any remaining devices as off
                            Object.keys(deviceList).forEach(function(key) {
                                if (key !== "SenseMonitor") {
                                    // totalUseArr.push(convUsage(deviceList[key].usage));

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

                            //Keep track of the total so we can send an overall total device
                            //Add in "total" device
                            // devArray.push({
                            //     id: 'TotalUsage',
                            //     name: 'TotalUsage',
                            //     state: 'on',
                            //     usage: 
                            // });

                            var secondsSinceLastPush = (Date.now() - lastPush.getTime()) / 1000;
                            // console.log('devArray: ', devArray);
                            //Override updateNow if it's been less than 15 seconds since our last push
                            if (secondsSinceLastPush <= minimumSecondsBetweenPush) {
                                updateNow = false;
                            }

                            if (updateNow || secondsSinceLastPush >= maximumSecondsBetweenPush) {
                                //console.log("Sending data to SmartThings hub");
                                //prevTotalUsage = convUsage(arrSum(totalUseArr)) || 0; //Save current total for future comparison
                                // console.log('PrevTotalUsage:', prevTotalUsage);
                                let otherMonData = {};
                                if (Object.keys(data.payload.voltage).length) {
                                    let v = [];
                                    v.push(convUsage(data.payload.voltage[0], 1));
                                    v.push(convUsage(data.payload.voltage[1], 1));
                                    otherMonData.voltage = v;
                                }

                                otherMonData.hz = convUsage(data.payload.hz, 0);
                                otherMonData.serverVersion = serverVersion;
                                updateMonitorInfo(convUsage(data.payload.w) || 0, otherMonData);
                                lastPush = new Date();
                                var options = {
                                    method: 'POST',
                                    uri: 'http://' + smartThingsHubIP + ':39500/event',
                                    body: {
                                        "devices": devArray
                                    },
                                    headers: {
                                        'source': 'STSense'
                                    },
                                    json: true // Automatically stringifies  the body to JSON
                                };

                                if (smartThingsAppId !== undefined || smartThingsAppId !== '') {
                                    options.headers.senseAppId = config.smartThingsAppId;
                                }
                                //Send to SmartThings!
                                request(options)
                                    .then(function() {
                                        tsLogger('**Sent Data for (' + devArray.length + ') Devices to SmartThings Hub Successfully!**');
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

// Runs this whole thing
getData();