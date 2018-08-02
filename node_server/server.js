
//Required settings
var email = "ReplaceWithYourSenseEmail";
var password = "ReplaceWithYourSensePassword";
var smartThingsHubIP = "ReplaceWithYourHubIP"

//Optional Settings
/*This process opens up a websocket connection to Sense; we see realtime data from Sense every couple seconds.
While parsing that data, we watch for significant changes so we can push to SmartThings only when relevant to avoid spamming the hub.
At baseline, we send an update at least once every 5 minutes (maximumsecondsBetweenPush).
We do NOT send updates less than 5 seconds apart (minimumSecondsBetweenPush).
We send an update when at least one device has turned on or off.
We send an update when power usage on a device has changed by at least 100 watts (usageThreshold).
*/
var usageThreshold = 100;    //Change in usage that determines when a special push to ST is made
var maximumsecondsBetweenPush = 300; //Maximum number of seconds between data pushes to ST
var minimumSecondsBetweenPush = 5; //Minimum number of seconds between data pushes to ST
var autoReconnect = true;

//Libraries
const sense = require('unofficial-sense');
var rp = require("request-promise");

//Global variables to keep track of various things
global.deviceList = {};
global.lastPush = new Date();
lastPush.setDate(lastPush.getDate()-1);
global.reconnectPending = false;

getData();

function getData(){
    //On initial load, get basic list of devices
    sense({
        email: email,
        password: password,
        verbose: false
    }).then((mySense) => {
        console.log("Successfully connected to Sense! Data incoming!")
        reconnectPending = false;
        mySense.getDevices().then(devices => {
            for (let dev of devices){
                deviceList[dev.id] = {
                    id: dev.id,
                    name: dev.name,
                    state: "unknown",
                    usage: -1,
                    refreshed: 0
                }
            }        
        })

        //Handle websocket data updates
        mySense.events.on('data', processData);        
        
        //Handle closures and errors        
        mySense.events.on('close', handleWSClose, {data: this, msg: 'Connection was closed.'});
        mySense.events.on('error', handleWSClose, {data: this, msg: 'An error occurred' + this});

        //mySense.events.on('close', (data) => {

        function handleWSClose(data){                        
            console.log(data.msg);
            mySense.events.removeListener('data', processData);
            mySense.events.removeListener('close', handleWSClose);
            mySense.events.removeListener('error', handleWSClose);            
            reconnect();
        }
        
        //Reconnect if option enabled and if a reconnect attempt is not already in progress
        function reconnect(){
            if (autoReconnect && !reconnectPending){
                reconnectPending = true;
                console.log("Reconnecting...");
                getData();
            }
        }

        function processData(data){
            if (data.type == "realtime_update"){            
                if (data.payload != undefined){
                    if (data.payload.devices != undefined){                        
                        let updateNow = false;

                        //Mark off saved list so we can detect which have been seen lately
                        Object.keys(deviceList).forEach(function (key){                            
                            deviceList[key].refreshed = 0;
                        })

                        //Loop over currently active devices and refresh the saved list
                        // We'll send data to SmartThings if status has changed or if usage has changed over a certain amount
                        for (let dev of data.payload.devices){
                            let prevState = deviceList[dev.id].state;
                            let prevUsage = deviceList[dev.id].usage;
                            let currentUsage = dev.w;
                            let usageDelta = currentUsage - prevUsage;                                                       
                            
                            if (prevState != "on" && prevState != "unknown" && deviceList[dev.id].name != "Other"){
                                console.log(dev.name + " turned on!");
                                updateNow = true;
                            }

                            if (prevUsage != -1 && Math.abs(usageDelta) > usageThreshold){
                                console.log(dev.name + " usage changed by " + usageDelta);
                                updateNow = true;
                            }
                            deviceList[dev.id].state = "on";
                            deviceList[dev.id].usage = dev.w;
                            deviceList[dev.id].refreshed = 1;
                        }
                        
                        //Keep track of the total so we can send an overall total device
                        let senseTotal = {
                            id: "TotalUsage",
                            name: "TotalUsage",
                            state: "on",
                            usage: 0
                        }

                        //Convert list to array for easier parsing in ST
                        var devArray = [];

                        //Loop over saved list again and mark any remaining devices as off
                        Object.keys(deviceList).forEach(function (key){
                            devArray.push(deviceList[key]);
                            senseTotal.usage = senseTotal.usage + deviceList[key].usage;
                            if (deviceList[key].refreshed == 0){
                                if (deviceList[key].state != "off" && deviceList[key].state != "unknown" && deviceList[key].name != "Other"){
                                    console.log(deviceList[key].name + " turned off!");
                                    updateNow = true;
                                }
                                deviceList[key].state = "off";
                                deviceList[key].usage = 0;
                            }
                        })

                        //Add in "total" device
                        devArray.push(senseTotal);

                        var secondsSinceLastPush = (Date.now() - lastPush.getTime()) / 1000
                        
                        //Override updateNow if it's been less than 15 seconds since our last push
                        if (secondsSinceLastPush <= minimumSecondsBetweenPush){updateNow = false;} 

                        if (updateNow || secondsSinceLastPush >= maximumsecondsBetweenPush){
                            //console.log("Sending data to SmartThings hub");
                            lastPush = new Date();
                            var options = {
                                method: 'POST',
                                uri: 'http://' + smartThingsHubIP + ':39500/event',
                                body: {"devices": devArray},
                                headers: {
                                    'source': 'STSense'
                                },
                                json: true // Automatically stringifies  the body to JSON
                            };
                            
                            //Send to SmartThings!
                            rp(options)
                                .then(function (parsedBody) {
                                        console.log(new Date().toLocaleString() + " - Data successfully sent to SmartThings hub!");
                                })
                                .catch(function (err) {
                                    console.log("Error sending data to ST: " & err);           
                                });
                        }
                    }
                }
            }
        }
    })
}

