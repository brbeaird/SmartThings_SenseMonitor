/**
 *	Sense Device
 *
 *	Author: Brian Beaird
 *  Last Updated: 2018-08-11
 *
 ***************************
 *
 *  Copyright 2018 Brian Beaird
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
 
metadata {
	definition (name: "Sense Monitor Device", namespace: "brbeaird", author: "Brian Beaird") {		
        capability "Power Meter"
        capability "Switch"
        capability "Actuator"
        capability "Sensor"
        attribute "lastUpdated", "string"
	}
    
    preferences {       
       input "prefNotifyOn", "bool", required: false, title: "Push notifications when turned on?"
       input "prefNotifyOnDelay", "number", required: false, title: "Delay notification until unit has remained ON for this many minutes"
       input "prefNotifyOff", "bool", required: false, title: "Push notifications when turned off?"
       input "prefNotifyOffDelay", "number", required: false, title: "Delay notification until unit has remained OFF for this many minutes"
    }

	tiles {
        
        standardTile("switch", "device.switch", width: 1, height: 1, canChangeIcon: true) {
            state "off", label: '${currentValue}', action: "switch.on",
                  icon: "st.switches.switch.off", backgroundColor: "#ffffff"
            state "on", label: '${currentValue}', action: "switch.off",
                  icon: "st.switches.switch.on", backgroundColor: "#00a0dc"
        }
        
        valueTile("power", "device.power", decoration: "flat", width: 1, height: 1) {
            state "power", label:'${currentValue} Watts'
        }
        
        valueTile("lastUpdated", "device.lastUpdated", height: 1, width: 2, inactiveLabel: false, decoration: "flat") {
			state("lastUpdatedValue", label:'Last updated: ${currentValue}', backgroundColor:"#ffffff")
		}
        
        main(["power", "switch"])
        details(["power", "switch", "lastUpdated"])
    }
}

//For testing only
/*
def on(){

	log.debug "Scheduling push notification for ON!"
    unschedule(checkForOnNotify)
    unschedule(checkForOffNotify)
    runIn(60*prefNotifyOnDelay, checkForOnNotify)
    state.OnNotificationIsPending = true
    state.lastTurnedOn = now()
}


def off(){
    log.debug "Updating status to off"
    //sendEvent(name: "switch", value: "off", display: true, displayed: true, isStateChange: true, descriptionText: device.displayName + " was off")    
    log.debug "Scheduling push notification for OFF!"
    unschedule(checkForOnNotify)
    unschedule(checkForOffNotify)
    runIn(60*prefNotifyOffDelay, checkForOffNotify)
    state.OffNotificationIsPending = true
    state.lastTurnedOff = now()
}
*/

def checkForOnNotify(){handlePendingNotification(state.lastTurnedOn, state.lastTurnedOff, "On", prefNotifyOnDelay)}
def checkForOffNotify(){handlePendingNotification(state.lastTurnedOff, state.lastTurnedOn, "Off", prefNotifyOffDelay)}


def handlePendingNotification(lastActivated, lastCanceled, actionName, delayPref){
    //If device has been canceled (opposite switch state activated) while we were waiting, bail out
    log.debug "Checking to see if pending " + actionName + " notification should be sent..."
    if (lastActivated == null){lastActivated = 0}
    if (lastCanceled == null){lastCanceled = 0}    
    if (lastActivated < lastCanceled){
    	log.debug "Device turned " + actionName + " before notification threshold. Notification canceled."
        return
    }
    
    def timeSinceLastChange = ((now() - lastActivated) / 1000) + 10
    def delayInSeconds = delayPref * 60    
    if (timeSinceLastChange >= delayInSeconds)
    {
    	log.debug "Sending " + actionName + " notification"
        parent.sendPushMessage(getShortDevName() + " turned " + actionName + " (" + (Math.round(timeSinceLastChange / 60)) +  " minutes ago.)")
        
        //if (actionName == "On"){state.OnNotificationIsPending = false}
        //if (actionName == "Off"){state.OffNotificationIsPending = false}
    }
    
    //If it's not time yet, reschedule a check
    else
    {
        def timeLeftTillNotify = delayInSeconds - timeSinceLastChange        
        if (timeLeftTillNotify < 60) {timeLeftTillNotify = 60}
        log.debug "Will check again in " + timeLeftTillNotify + " seconds"
    	if (actionName == "On"){runIn(timeLeftTillNotify, checkForOnNotify)}
        if (actionName == "Off"){runIn(timeLeftTillNotify, checkForOffNotify)}
    }
}

def updateDeviceLastRefresh(lastRefresh){
    log.debug "Last refresh: " + lastRefresh
    
    def refreshDate = new Date()
    def hour = refreshDate.format("h", location.timeZone)
    def minute =refreshDate.format("m", location.timeZone)
    def ampm =refreshDate.format("a", location.timeZone)
    //def finalString = refreshDate.getDateString() + ' ' + hour + ':' + minute + ampm
    
    def finalString = new Date().format('MM/d/yyyy hh:mm',location.timeZone)
    sendEvent(name: "lastRefresh", value: finalString, display: false , displayed: false)
}


def getShortDevName(){
	return device.displayName.replace("Sense-", "")
}

def updateDeviceStatus(senseDevice){
	def devName = getShortDevName()
    def oldStatus = device.currentValue("switch")    
    
    //log.debug "Old status was " + oldStatus
    //log.debug "New status is: " + senseDevice.state
    
    def statusChange = false
    
    if (oldStatus != senseDevice.state){statusChange = true}
    
    //If on/off status has changed
    if (statusChange){
    	 if (senseDevice.state == "off"){
             log.debug "Updating status to off"
             sendEvent(name: "switch", value: "off", display: true, displayed: true, isStateChange: true, descriptionText: device.displayName + " was off")
             if (prefNotifyOff && !(parent.quietModes.contains(location.currentMode))){
                 //Depending on prefs, send notification immediately or schedule after delay
                 if (prefNotifyOffDelay == null || prefNotifyOffDelay == 0){
                     parent.sendPushMessage(devName + " turned off!")
                 }
                 else{
                     log.debug "Scheduling OFF push notification!"
                     unschedule(checkForOnNotify)
                     unschedule(checkForOffNotify)
                     runIn(60*prefNotifyOffDelay, checkForOffNotify)
                     //state.OffNotificationIsPending = true
                     state.lastTurnedOff = now()
                 }
             }
        }
        if (senseDevice.state == "on"){
            log.debug "Updating status to on"
            sendEvent(name: "switch", value: "on", display: true, displayed: true, isStateChange: true, descriptionText: device.displayName + " was on")
            if (prefNotifyOn && !(parent.quietModes.contains(location.currentMode))){                                
                //Depending on prefs, send notification immediately or schedule after delay
                if (prefNotifyOnDelay == null || prefNotifyOnDelay == 0){
                    parent.sendPushMessage(devName + " turned on!")
                }
                else{
                    log.debug "Scheduling ON push notification!"
                    unschedule(checkForOnNotify)
                    unschedule(checkForOffNotify)
                    runIn(60*prefNotifyOnDelay, checkForOnNotify)
                    //state.OnNotificationIsPending = true
                    state.lastTurnedOn = now()
                }
            }
        }
    }
   
   	def currentPower = senseDevice.usage
    def oldPower = device.currentValue("power")
    //def usageChangeThreshold = 100
    //if (devName == "Always On"){usageChangeThreshold = 1}    
    //if (oldPower == null){oldPower = 0}
    //if (oldPower == null){oldPower = 0}
    
    //Also update usage if status has changed or if usage value has changed by more than 100 watts
    //if (statusChange || oldPower == 0 || (currentPower - oldPower).abs() >= usageChangeThreshold){    
    
    //Decide if we should update the usage
    if (oldPower != currentPower){
        def isUsageChange = true
        def usageChange = (currentPower - oldPower).abs()
    	if (devName == "Other" && usageChange < 30 && currentPower != 0){isUsageChange = false}
        if (devName == "TotalUsage" && usageChange < 50 && currentPower != 0){isUsageChange = false}
        
        if (isUsageChange){        
            log.debug "Updating usage from " + oldPower + " to " + currentPower
            sendEvent(name: "power", value: currentPower, display: true, displayed: true, isStateChange: true)
        }
    }
    
    updateDeviceLastRefresh()
}

def updateDeviceLastRefresh(){    
    
    def finalString = new Date().format('MM/d/yyyy hh:mm',location.timeZone)
    sendEvent(name: "lastUpdated", value: finalString, display: false , displayed: false)
}

def log(msg){
	log.debug msg
}

def showVersion(){
	return "0.0.1"
}
