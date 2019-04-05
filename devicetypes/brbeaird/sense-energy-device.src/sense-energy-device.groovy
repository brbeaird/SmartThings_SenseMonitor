/**
 *	Sense Device
 *
 *	Author: Brian Beaird and Anthony Santilli
 *  Last Updated: 2019-04-04
 *
 ***************************
 *
 *  Copyright 2019 Brian Beaird and Anthony Santilli
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

import java.text.SimpleDateFormat
String devVersion() { return "0.3.3"}
String devModified() { return "2019-04-04"}
String gitAuthor() { return "brbeaird" }
String getAppImg(imgName) { return "https://raw.githubusercontent.com/${gitAuthor()}/SmartThings_SenseMonitor/master/resources/icons/$imgName" }

metadata {
    definition (name: "Sense Energy Device", namespace: "brbeaird", author: "Anthony Santilli & Brian Beaird", vid: "generic-power") {
        capability "Power Meter"
        capability "Switch"
        capability "Actuator"
        capability "Sensor"
        
        attribute "lastUpdated", "string"
        attribute "deviceLocation", "string"
        attribute "dtCreated", "string"
        attribute "deviceMake", "string"
        attribute "deviceModel", "string"
        attribute "detectionMature", "string"
        attribute "deviceRevoked", "string"
        attribute "onCountToday", "string"
        attribute "onCountWeek", "string"
    }

    preferences {
        input "prefNotifyOn", "bool", required: false, title: "Notify when turned on?", description: "Uses the Apps Notification Settings to Choose the destinations"
        input "prefNotifyOnDelay", "number", required: false, title: "Delay notification until unit has remained ON for this many minutes"
        input "prefNotifyOff", "bool", required: false, title: "Notify when turned off?", description: "Uses the Apps Notification Settings to Choose the destinations"
        input "prefNotifyOffDelay", "number", required: false, title: "Delay notification until unit has remained OFF for this many minutes"
        input "prefNotifyUsageOverVal", "number", required: false, title: "Notify when usage is over this value?", description: "Uses the Apps Notification Settings to Choose the destinations"
        input "prefNotifyUsageOverDelay", "number", required: false, title: "Delay notification until unit has remains over for this many minutes"
        input "showLogs", "bool", required: false, title: "Show Debug Logs?", defaultValue: false
    }

    tiles (scale: 2) {
        multiAttributeTile(name:"genericMulti", type:"generic", width:6, height:4) {
            tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label: '${currentValue}', icon: "st.switches.switch.on", backgroundColor: "#00a0dc"
                attributeState "off", label: '${currentValue}', icon: "st.switches.switch.off", backgroundColor: "#ffffff"
            }
            tileAttribute("device.power", key: "SECONDARY_CONTROL") {
                attributeState "power", label: '${currentValue}W', unit: "W",
                        foregroundColor: "#000000",
                        backgroundColors:[
                            [value: 1, color: "#00cc00"], //Light Green
                            [value: 2000, color: "#79b821"], //Darker Green
                            [value: 3000, color: "#ffa81e"], //Orange
                            [value: 4000, color: "#FFF600"], //Yellow
                            [value: 5000, color: "#fb1b42"] //Bright Red
                        ]
            }
        }
        valueTile("power", "device.power", decoration: "flat", width: 1, height: 1) {
            state "power", label:'${currentValue} W', backgroundColors:[[value: 0, color: "#ffffff"],[value: 1, color: "#00a0dc"]], icon: "https://raw.githubusercontent.com/tonesto7/SmartThings_SenseMonitor/master/resources/icons/sense_energy.png"
        }
        valueTile("blank1", "device.blank", height: 1, width: 1, inactiveLabel: false, decoration: "flat") {
            state("default", label:'')
        }
        valueTile("blank2", "device.blank", height: 1, width: 2, inactiveLabel: false, decoration: "flat") {
            state("default", label:'')
        }
        valueTile("lastUpdated", "device.lastUpdated", height: 1, width: 3, inactiveLabel: false, decoration: "flat") {
            state("lastUpdated", label:'Last Updated:\n${currentValue}')
        }
        valueTile("deviceLocation", "device.deviceLocation", height: 1, width: 2, inactiveLabel: false, decoration: "flat") {
            state("deviceLocation", label:'Device Location:\n${currentValue}')
        }
        valueTile("dtCreated", "device.dtCreated", height: 1, width: 3, inactiveLabel: false, decoration: "flat") {
            state("dtCreated", label:'Device Created:\n${currentValue}')
        }
        valueTile("deviceMake", "device.deviceMake", height: 1, width: 2, inactiveLabel: false, decoration: "flat") {
            state("deviceMake", label:'Device Make:\n${currentValue}')
        }
        valueTile("deviceModel", "device.deviceModel", height: 1, width: 2, inactiveLabel: false, decoration: "flat") {
            state("deviceModel", label:'Device Model:\n${currentValue}')
        }
        valueTile("detectionMature", "device.detectionMature", height: 1, width: 2, inactiveLabel: false, decoration: "flat") {
            state("detectionMature", label:'Detection Confirmed:\n${currentValue}')
        }
        valueTile("onCountToday", "device.onCountToday", height: 1, width: 2, inactiveLabel: false, decoration: "flat") {
            state("onCountToday", label:'On Count (Today):\n${currentValue}')
        }
        valueTile("onCountWeek", "device.onCountWeek", height: 1, width: 2, inactiveLabel: false, decoration: "flat") {
            state("onCountWeek", label:'On Count (Week):\n${currentValue}')
        }
        valueTile("deviceRevoked", "device.deviceRevoked", height: 1, width: 2, inactiveLabel: false, decoration: "flat") {
            state("deviceRevoked", label:'Device Valid:\n${currentValue}')
        }
        main(["power"])
        details(["genericMulti", "lastUpdated", "dtCreated", "deviceLocation", "deviceMake", "deviceModel", "detectionMature", "deviceRevoked", "onCountToday", "onCountWeek"])
    }
}

def installed() {
	log.trace "${device?.displayName} Installed..."
    def dt = formatDt(new Date(), "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    state?.dateCreated = dt
	initialize()
}

def updated() {
	log.trace "${device?.displayName} Updated..."
	initialize()
}

def initialize() {
    unschedule()
	log.trace "${device?.displayName} initialize"
 	sendEvent(name: "DeviceWatch-DeviceStatus", value: "online")
	sendEvent(name: "DeviceWatch-Enroll", value: [protocol: "cloud", scheme:"untracked"].encodeAsJson(), displayed: false)
    if(!(device?.displayName in ["Sense-Other", "Sense-Always On"])) {
        if(device?.currentState("onCountToday")?.value == null) { sendEvent(name: "onCountToday", value: 0, display: false, displayed: false) }
        if(device?.currentState("onCountWeek")?.value == null) { sendEvent(name: "onCountWeek", value: 0, display: false, displayed: false) }
        schedule("0 0 0 1/1 * ? *", "resetOnCount")
        schedule("0 0 0 ? * MON *", "resetOnCountWeek")
    }

}

def on(){
    log.trace "On() is not an actionable command for this device"
    // log.debug "Scheduling push notification for ON!"
    // unschedule(checkForOnNotify)
    // unschedule(checkForOffNotify)
    // runIn(60*settings?.prefNotifyOnDelay, checkForOnNotify)
    // state.OnNotificationIsPending = true
    // state.lastTurnedOn = now()
}


def off() {
    log.trace "Off() is not an actionable command for this device"
    // log.debug "Updating status to off"
    //sendEvent(name: "switch", value: "off", display: true, displayed: true, isStateChange: true, descriptionText: device.displayName + " was off")
    // log.debug "Scheduling push notification for OFF!"
    // unschedule(checkForOnNotify)
    // unschedule(checkForOffNotify)
    // runIn(60*settings?.prefNotifyOffDelay, checkForOffNotify)
    // state.OffNotificationIsPending = true
    // state.lastTurnedOff = now()
}

def toggleOn(){
	log.debug "toggled on"
    sendEvent(name: "switch", value: "on", display: true, displayed: true, isStateChange: true, descriptionText: device?.displayName + " was on")
}

def toggleOff(){
log.debug "toggled off"
	sendEvent(name: "switch", value: "off", display: true, displayed: true, isStateChange: true, descriptionText: device?.displayName + " was off")
}
private resetOnCount() {
    log.trace "resetOnCount"
    state?.onCountToday = 0
}

private resetOnCountWeek() {
    log.trace "resetOnCountWeek"
    state?.onCountWeek = 0
}

public incrementOnCnt() {
    if(!(device?.displayName in ["Sense-Other", "Sense-Always On"])) {
        // log.trace "incrementOnCnt (before) | dayCnt: ${state?.onCountToday} | weekCnt: ${state?.onCountWeek}"
        state?.onCountToday = state?.onCountToday?.isNumber() && state?.onCountToday >= 1 ? state?.onCountToday?.toInteger() + 1 : 1
        state?.onCountWeek = state?.onCountWeek?.isNumber() && state?.onCountWeek >= 1 ? state?.onCountWeek?.toInteger() + 1 : 1
        // log.trace "incrementOnCnt (after) | dayCnt: ${state?.onCountToday} | weekCnt: ${state?.onCountWeek}"
        if(isStateChange(device, "onCountToday", state?.onCountToday?.toString())) {
            sendEvent(name: "onCountToday", value: state?.onCountToday, display: false, displayed: false)
        }
        if(isStateChange(device, "onCountWeek", state?.onCountWeek?.toString())) {
            sendEvent(name: "onCountWeek", value: state?.onCountWeek, display: false, displayed: false)
        }
    }
}

def checkForOnNotify(){handlePendingNotification(state.lastTurnedOn, state.lastTurnedOff, "On", settings?.prefNotifyOnDelay)}
def checkForOffNotify(){handlePendingNotification(state.lastTurnedOff, state.lastTurnedOn, "Off", settings?.prefNotifyOffDelay)}
def checkForUsageOverNotify(){handlePendingNotification(state.lastUsageOverOn, state.lastTurnedOff, "On", settings?.prefNotifyOnDelay)}

def handlePendingNotification(lastActivated, lastCanceled, actionName, delayPref){
    //If device has been canceled (opposite switch state activated) while we were waiting, bail out
    logger("debug", "Checking to see if pending " + actionName + " notification should be sent...")
    if (lastActivated == null){lastActivated = 0}
    if (lastCanceled == null){lastCanceled = 0}
    if (lastActivated < lastCanceled){
        log.debug "Device turned " + actionName + " before notification threshold. Notification canceled."
        return
    }

    Integer timeSinceLastChange = ((now() - lastActivated) / 1000) + 10
    Integer delayInSeconds = delayPref * 60
    if (timeSinceLastChange >= delayInSeconds) {
        logger("debug", "Sending " + actionName + " notification")
        // sendMsg(String msgType, String msg, Boolean showEvt=true, Map pushoverMap=null, sms=null, push=null)
        parent?.sendMsg("Sense Device Alert", "${getShortDevName()} turned ${actionName} (${(Math.round(timeSinceLastChange / 60))} minutes ago.)")

        //if (actionName == "On"){state.OnNotificationIsPending = false}
        //if (actionName == "Off"){state.OffNotificationIsPending = false}
    } else { //If it's not time yet, reschedule a check
        Integer timeLeftTillNotify = (delayInSeconds - timeSinceLastChange)
        if (timeLeftTillNotify < 60) { timeLeftTillNotify = 60 }
        logger("debug", "Will check again in $timeLeftTillNotify seconds")
        if (actionName == "On") { runIn(timeLeftTillNotify, checkForOnNotify) }
        if (actionName == "Off") { runIn(timeLeftTillNotify, checkForOffNotify) }
    }
}

def getShortDevName(){
    return device?.displayName?.replace("Sense-", "")
}

def updateDeviceStatus(Map senseDevice){
    String devName = getShortDevName()
    String oldStatus = device.currentValue("switch")
    //log.debug "Old status was " + oldStatus
    //log.debug "New status is: " + senseDevice.state
    if(isStateChange(device, "switch", senseDevice?.state?.toString())) {
        //If on/off status has changed
        if (senseDevice?.state == "off") {
            logger("debug", "Change Switch Status to: (OFF)")
            sendEvent(name: "switch", value: "off", display: true, displayed: true, isStateChange: true, descriptionText: device?.displayName + " was off")
            if (settings?.prefNotifyOff && ok2Notify()) {
                //Depending on prefs, send notification immediately or schedule after delay
                if (settings?.prefNotifyOffDelay == null || settings?.prefNotifyOffDelay == 0){
                    parent?.sendMsg("Sense Device Alert", "${devName} turned off!")
                } else {
                    logger("debug", "Scheduling OFF Notification!")
                    unschedule(checkForOnNotify)
                    unschedule(checkForOffNotify)
                    runIn(60*settings?.prefNotifyOffDelay, checkForOffNotify)
                    //state.OffNotificationIsPending = true
                    state.lastTurnedOff = now()
                }
            }
        }
        if (senseDevice?.state == "on") {
            logger("debug", "Change Switch Status to: (ON)")
            sendEvent(name: "switch", value: "on", display: true, displayed: true, isStateChange: true, descriptionText: device?.displayName + " was on")
            incrementOnCnt()
            if (settings?.prefNotifyOn && ok2Notify()){
                //Depending on prefs, send notification immediately or schedule after delay
                if (settings?.prefNotifyOnDelay == null || settings?.prefNotifyOnDelay == 0){
                    parent?.sendMsg("Sense Device Alert", "${devName} turned on!")
                } else{
                    logger("debug", "Scheduling ON Notification!")
                    unschedule(checkForOnNotify)
                    unschedule(checkForOffNotify)
                    runIn(60*settings?.prefNotifyOnDelay, checkForOnNotify)
                    //state.OnNotificationIsPending = true
                    state.lastTurnedOn = now()
                }
            }
        }
    }

    Float currentPower = senseDevice?.usage?.isNumber() ? senseDevice?.usage as Float : 0.0
    Float oldPower = device.currentState("power")?.floatValue ?: 0
    // Boolean usageIsOverAlert = (settings?.prefNotifyUsageOverVal && (currentPower > settings?.prefNotifyUsageOverVal?.toFloat()))
    // if (settings?.prefNotifyUsageOverVal) {
    //      if (usageIsOverAlert && ok2Notify()) {
    //         //Depending on prefs, send notification immediately or schedule after delay
    //         if (settings?.prefNotifyUsageOverDelay == null || settings?.prefNotifyUsageOverDelay == 0){
    //             parent?.sendMsg("Sense Device Alert", "${devName} current usage (${current}W) has exceeded the ${settings?.prefNotifyUsageOverVal}W alert setting!")
    //         } else{
    //             logger("debug", "Scheduling Usage Over Notification!")
    //             unschedule(checkForUsageOverNotify)
    //             runIn(60*settings?.prefNotifyUsageOverDelay, checkForUsageOverNotify)
    //             state.lastUsageOverOn = now()
    //         }
    //      } else { unschedule(checkForUsageOverNotify) }
    // }

    // log.debug "usage: ${senseDevice?.usage} | currentPower: $currentPower | oldPower: ${oldPower}"
    
    if(senseDevice?.containsKey("dateCreated")) {
        def dtCreated = senseDevice?.dateCreated ? formatDt(parseDt(senseDevice?.dateCreated, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")) : (state?.dateCreated ? formatDt(parseDt(state?.dateCreated, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")) : "")
        if(isStateChange(device, "dtCreated", dtCreated as String)) {
            sendEvent(name: "dtCreated", value: dtCreated as String, display: true, displayed: true)
        }
    }

    String loc = senseDevice?.location ?: "Not Set"
    if(isStateChange(device, "deviceLocation", loc?.toString())) {
        sendEvent(name: "deviceLocation", value: loc?.toString(), display: true, displayed: true)
    }

    String make = senseDevice?.make ?: "Not Set"
    if(isStateChange(device, "deviceMake", make?.toString())) {
        sendEvent(name: "deviceMake", value: make?.toString(), display: true, displayed: true)
    }

    String model = senseDevice?.model ?: "Not Set"
    if(isStateChange(device, "deviceModel", model?.toString())) {
        sendEvent(name: "deviceModel", value: model?.toString(), display: true, displayed: true)
    }
    if(senseDevice?.containsKey("revoked")) {
        if(isStateChange(device, "deviceRevoked", (senseDevice?.revoked != true)?.toString()?.capitalize())) {
            sendEvent(name: "deviceRevoked", value: (senseDevice?.deviceRevoked != true)?.toString()?.capitalize(), display: true, displayed: true)
        }
    }
    if(senseDevice?.containsKey("mature")) {
        if(isStateChange(device, "detectionMature", (senseDevice?.mature == true)?.toString()?.capitalize())) {
            sendEvent(name: "detectionMature", value: (senseDevice?.mature == true)?.toString()?.capitalize(), display: true, displayed: true)
        }
    }
    
    // log.debug "currentPower: ${currentPower} | oldPower: ${oldPower}"
    if (oldPower != currentPower) {
        if (isStateChange(device, "power", currentPower?.toString())) {
            logger("debug", "Updating Power Usage from ${oldPower}W to ${currentPower}W")
            def showlog = false
            if (usageChange > 100)
            	showlog = true
            sendEvent(name: "power", value: currentPower, units: "W", display: showlog, displayed: showlog, isStateChange: true)
        }
    }
    setOnlineStatus((senseDevice?.revoked == true) ? false : true)
    sendEvent(name: "lastUpdated", value: formatDt(new Date()), display: false , displayed: false)
}

public setOnlineStatus(Boolean isOnline) {
    if(isStateChange(device, "DeviceWatch-DeviceStatus", (isOnline ? "online" : "offline"))) {
        sendEvent(name: "DeviceWatch-DeviceStatus", value: (isOnline ? "online" : "offline"), displayed: true, isStateChange: true)
    }
}

def formatDt(dt, String tzFmt="MM/d/yyyy h:mm:ss a") {
	def tf = new SimpleDateFormat(tzFmt)
    if(location?.timeZone) { tf.setTimeZone(location?.timeZone) }
    return tf.format(dt)
}

def parseDt(dt, dtFmt) {
    return Date.parse(dtFmt, dt)
}

Boolean ok2Notify() {
    return (parent?.getOk2Notify())
}

private logger(type, msg) {
	if(type && msg && settings?.showLogs) {
		log."${type}" "${msg}"
	}
}
