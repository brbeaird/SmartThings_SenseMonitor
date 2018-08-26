/**
 *	Sense Monitor SmartApp
 *
 *	Author: Brian Beaird
 *  Last Updated: 2018-08-26 (By A. Santilli)
 *
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
import java.text.SimpleDateFormat
include 'asynchttp_v1'

String appVersion() { return "0.2.0" }
String appAuthor() { return "Brian Beaird" }
String gitBranch() { return "tonesto7" }
String getAppImg(imgName) 	{ return "https://raw.githubusercontent.com/${gitBranch()}/SmartThings_SenseMonitor/master/resources/icons/$imgName" }

definition(
    name: "SenseMonitor",
    namespace: "brbeaird",
    author: "Brian Beaird",
    description: "Connects SmartThings with Sense",
    category: "My Apps",
    iconUrl: "https://raw.githubusercontent.com/${gitBranch()}/SmartThings_SenseMonitor/master/resources/icons/sense.1x.png",
    iconX2Url: "https://raw.githubusercontent.com/${gitBranch()}/SmartThings_SenseMonitor/master/resources/icons/sense.2x.png",
    iconX3Url: "https://raw.githubusercontent.com/${gitBranch()}/SmartThings_SenseMonitor/master/resources/icons/sense.3x.png")


preferences {
    // page(name: "prefConfigure", title: "Sense")
	page(name: "mainPage")
	page(name: "prefsPage")
	page(name: "debugPrefPage")
	page(name: "notifPrefPage")
	page(name: "setNotificationTimePage")
	page(name: "uninstallPage")
	page(name: "infoPage")
	page(name: "changeLogPage")
	page(name: "savePage")
    //TODO: Add version Checking
    //TODO: Add preference to exclude Sense devices
    //TODO: Add preference to NOT auto re-sync names
}

def appInfoSect(sect=true)	{
	def str = ""
    str += "${app?.name}"
    str += "\nAuthor: ${appAuthor()}"
    str += "\nVersion: ${state.thisSmartAppVersion}"
    section() { paragraph str, image: getAppImg("sense.2x.png") }
}

def prefConfigure() {
    checkVersionData(true)
    state.previousVersion = state.thisSmartAppVersion
    if (state.previousVersion == null){
        state.previousVersion = 0;
    }
    state.thisSmartAppVersion = "0.1.0"
    getVersionInfo(0, 0)

    return dynamicPage(name: "prefConfigure", title: "Configure Sense Devices", uninstall:true, install: true) {
        appInfoSect()
        section("Discovered Devices:") {
            List devs = getDeviceList()?.collect { "${it?.value?.name} (${it?.value?.usage ?: 0} W)" }?.sort()
            paragraph "${devs?.size() ? devs?.join("\n") : "No Devices Available"}"
        }
        section("Notification Filters:") {
            input "quietModes", "mode", title: "Don't Notify in These Modes!", multiple: true, submitOnChange: true
        }
    }
}

def mainPage() {
    checkVersionData(true)
	dynamicPage(name: "mainPage", uninstall: false, install: true) {
        appInfoSect()
        section("Discovered Devices:") {
            List devs = getDeviceList()?.collect { "${it?.value?.name} (${it?.value?.usage ?: 0} W)" }?.sort()
            paragraph "${devs?.size() ? devs?.join("\n") : "No Devices Available"}"
        }
        section("Notifications:") {
            def t0 = getAppNotifConfDesc()
            href "notifPrefPage", title: "Notifications", description: (t0 ? "${t0}\n\nTap to modify" : "Tap to configure"), state: (t0 ? "complete" : null), image: getAppImg("notification_icon2.png")
        }
        section("Currency and Logging:") {
            def descStr = ""
            def sz = descStr.size()
            descStr += getAppDebugDesc() ?: ""
            if(descStr.size() != sz) { descStr += "\n"; sz = descStr.size() }
            def prefDesc = (descStr != "") ? "${descStr}Tap to Modify..." : "Tap to Configure..."
            href "prefsPage", title: "Preferences", description: prefDesc, state: (descStr ? "complete" : ""), image: getAppImg("settings_icon.png")
        }
        section("") {
            href "uninstallPage", title: "Uninstall this App", description: "Tap to Remove...", image: getAppImg("uninstall_icon.png")
        }
	}
}

Map getDeviceList(isInputEnum=false, hideDefaults=true) {
    Map devMap = [:]
    Map availDevs = atomicState?.senseDeviceMap ?: [:]
    availDevs?.each { key, val->
        if(hideDefaults) { 
            if(!(key?.toString() in ["TotalUsage", "unknown", "SenseMonitor", "always_on"])) {
                devMap[key] = val
            }
        } else { devMap[key] = val }
    }
    return isInputEnum ? (devMap?.size() ? devMap?.collectEntries { [(it?.key):it?.value?.name] } : devMap) : devMap
}

def debugPrefPage() {
	dynamicPage(name: "debugPrefPage", install: false) {
		section ("Application Logs") {
			input (name: "appDebug", type: "bool", title: "Show App Logs in the IDE?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("log.png"))
			if (settings?.appDebug) {
				LogAction("Debug Logs are Enabled...", "info", false)
			} else { LogAction("Debug Logs are Disabled...", "info", false) }
		}
		section ("Child Device Logs") {
			input (name: "childDebug", type: "bool", title: "Show Device Logs in the IDE?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("log.png"))
			if (settings?.childDebug) { LogAction("Device Debug Logs are Enabled...", "info", false) 
			} else { LogAction("Device Debug Logs are Disabled...", "info", false) }
		}
	}
}

def notifPrefPage() {
	dynamicPage(name: "notifPrefPage", install: false) {
		section("Enable Text Messaging:") {
			input "phones", "phone", title: "Send SMS to Number\n(Optional)", required: false, submitOnChange: true, image: getAppImg("notification_icon2.png")
		}
		section("Enable Push Messages:") {
			input "usePush", "bool", title: "Send Push Notitifications\n(Optional)", required: false, submitOnChange: true, defaultValue: false, image: getAppImg("notification_icon.png")
		}
		section("Enable Pushover Support:") {
			input ("pushoverEnabled", "bool", title: "Use Pushover Integration", required: false, submitOnChange: true, image: getAppImg("pushover_icon.png"))
			if(settings?.pushoverEnabled == true) {
				if(state?.isInstalled) {
					if(!state?.pushoverManager) {
						paragraph "If this is the first time enabling Pushover than leave this page and come back if the devices list is empty"
						pushover_init()
					} else {
						input "pushoverDevices", "enum", title: "Select Pushover Devices", description: "Tap to select", groupedOptions: getPushoverDevices(), multiple: true, required: false, submitOnChange: true
						if(settings?.pushoverDevices) {
							def t0 = [(-2):"Lowest", (-1):"Low", 0:"Normal", 1:"High", 2:"Emergency"]
							input "pushoverPriority", "enum", title: "Notification Priority (Optional)", description: "Tap to select", defaultValue: 0, required: false, multiple: false, submitOnChange: true, options: t0
							input "pushoverSound", "enum", title: "Notification Sound (Optional)", description: "Tap to select", defaultValue: "pushover", required: false, multiple: false, submitOnChange: true, options: getPushoverSounds()
						}
					}
				} else { paragraph "New Install Detected!!!\n\n1. Press Done to Finish the Install.\n2. Goto the Automations Tab at the Bottom\n3. Tap on the SmartApps Tab above\n4. Select ${app?.getLabel()} and Resume configuration", state: "complete" }
			}
		}
		if(settings?.phone || settings?.usePush || (settings?.pushoverEnabled && settings?.pushoverDevices)) {
			if((settings?.usePush || (settings?.pushoverEnabled && settings?.pushoverDevices)) && !state?.pushTested && state?.pushoverManager) {
				if(sendMsgNew("Info", "Push Notification Test Successful. Notifications Enabled for ${app?.label}", true)) {
					state.pushTested = true
				}
			}
			section("Notification Restrictions:") {
				def t1 = getNotifSchedDesc()
				href "setNotificationTimePage", title: "Notification Restrictions", description: (t1 ?: "Tap to configure"), state: (t1 ? "complete" : null), image: getAppImg("restriction_icon.png")
			}

			section("Missed Poll Alerts:") {
				input (name: "sendMissedPollMsg", type: "bool", title: "Send Missed Poll Messages?", defaultValue: true, submitOnChange: true, image: getAppImg("late_icon.png"))
				if(sendMissedPollMsg == null || sendMissedPollMsg) {
					def misPollNotifyWaitValDesc = !misPollNotifyWaitVal ? "Default: 15 Minutes" : misPollNotifyWaitVal
					input (name: "misPollNotifyWaitVal", type: "enum", title: "Time Past the missed Poll?", required: false, defaultValue: 900, metadata: [values:notifValEnum()], submitOnChange: true)
					if(misPollNotifyWaitVal) {
						state.misPollNotifyWaitVal = !misPollNotifyWaitVal ? 900 : misPollNotifyWaitVal.toInteger()
						if (misPollNotifyWaitVal.toInteger() == 1000000) {
							input (name: "misPollNotifyWaitValCust", type: "number", title: "Custom Missed Poll Value in Seconds", range: "60..86400", required: false, defaultValue: 900, submitOnChange: true)
							if(misPollNotifyWaitValCust) { state?.misPollNotifyWaitVal = misPollNotifyWaitValCust ? misPollNotifyWaitValCust.toInteger() : 900 }
						}
					} else { state.misPollNotifyWaitVal = !misPollNotifyWaitVal ? 900 : misPollNotifyWaitVal.toInteger() }

					def misPollNotifyMsgWaitValDesc = !misPollNotifyMsgWaitVal ? "Default: 1 Hour" : misPollNotifyMsgWaitVal
					input (name: "misPollNotifyMsgWaitVal", type: "enum", title: "Delay before sending again?", required: false, defaultValue: 3600, metadata: [values:notifValEnum()], submitOnChange: true)
					if(misPollNotifyMsgWaitVal) {
						state.misPollNotifyMsgWaitVal = !misPollNotifyMsgWaitVal ? 3600 : misPollNotifyMsgWaitVal.toInteger()
						if (misPollNotifyMsgWaitVal.toInteger() == 1000000) {
							input (name: "misPollNotifyMsgWaitValCust", type: "number", title: "Custom Msg Wait Value in Seconds", range: "60..86400", required: false, defaultValue: 3600, submitOnChange: true)
							if(misPollNotifyMsgWaitValCust) { state.misPollNotifyMsgWaitVal = misPollNotifyMsgWaitValCust ? misPollNotifyMsgWaitValCust.toInteger() : 3600 }
						}
					} else { state.misPollNotifyMsgWaitVal = !misPollNotifyMsgWaitVal ? 3600 : misPollNotifyMsgWaitVal.toInteger() }
				}
			}
			section("Code Update Alerts:") {
				input (name: "sendAppUpdateMsg", type: "bool", title: "Send for Updates...", defaultValue: true, submitOnChange: true, image: getAppImg("update_icon.png"))
				if(sendMissedPollMsg == null || sendAppUpdateMsg) {
					def updNotifyWaitValDesc = !updNotifyWaitVal ? "Default: 2 Hours" : updNotifyWaitVal
					input (name: "updNotifyWaitVal", type: "enum", title: "Send reminders every?", required: false, defaultValue: 7200, metadata: [values:notifValEnum()], submitOnChange: true)
					if(updNotifyWaitVal) {
						state.updNotifyWaitVal = !updNotifyWaitVal ? 7200 : updNotifyWaitVal.toInteger()
						if (updNotifyWaitVal.toInteger() == 1000000) {
							input (name: "updNotifyWaitValCust", type: "number", title: "Custom Missed Poll Value in Seconds", range: "30..86400", required: false, defaultValue: 7200, submitOnChange: true)
							if(updNotifyWaitValCust) { state.updNotifyWaitVal = updNotifyWaitValCust ? updNotifyWaitValCust.toInteger() : 7200 }
						}
					} else { state.updNotifyWaitVal = !updNotifyWaitVal ? 7200 : updNotifyWaitVal.toInteger() }
				}
			}
		} else { state.pushTested = false }
	}
}

def setNotificationTimePage() {
	dynamicPage(name: "setNotificationTimePage", title: "Prevent Notifications\nDuring these Days, Times or Modes", uninstall: false) {
		def timeReq = (settings["qStartTime"] || settings["qStopTime"]) ? true : false
		section() {
			input "qStartInput", "enum", title: "Starting at", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: null, submitOnChange: true, required: false, image: getAppImg("start_time_icon.png")
			if(settings["qStartInput"] == "A specific time") {
				input "qStartTime", "time", title: "Start time", required: timeReq, image: getAppImg("start_time_icon.png")
			}
			input "qStopInput", "enum", title: "Stopping at", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: null, submitOnChange: true, required: false, image: getAppImg("stop_time_icon.png")
			if(settings?."qStopInput" == "A specific time") {
				input "qStopTime", "time", title: "Stop time", required: timeReq, image: getAppImg("stop_time_icon.png")
			}
			input "quietDays", "enum", title: "Only on these days of the week", multiple: true, required: false, image: getAppImg("day_calendar_icon.png"),
					options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
			input "quietModes", "mode", title: "When these Modes are Active", multiple: true, submitOnChange: true, required: false, image: getAppImg("mode_icon.png")
		}
	}
}

def changeLogPage () {
	dynamicPage(name: "changeLogPage", title: "View Change Info", install: false) {
		section("App Revision History:") {
			paragraph appVerInfo()
		}
	}
}

def uninstallPage() {
	dynamicPage(name: "uninstallPage", title: "Uninstall", uninstall: true) {
		section("") {
			paragraph "This will uninstall the App and All Child Devices.\n\nPlease make sure that any devices created by this app are removed from any routines/rules/smartapps before tapping Remove."
		}
		remove("Remove ${app?.label} and Devices!", "WARNING!!!", "Last Chance to Stop!\nThis action is not reversible\n\nThis App and Devices will be removed")
	}
}

def getAppNotifConfDesc() {
	def str = ""
	if(pushStatus()) {
		def ap = getAppNotifDesc()
		def nd = getNotifSchedDesc()
		str += (settings?.usePush) ? "${str != "" ? "\n" : ""}Sending via: (Push)" : ""
		str += (settings?.pushoverEnabled) ? "${str != "" ? "\n" : ""}Pushover: (Enabled)" : ""
		str += (settings?.pushoverEnabled && settings?.pushoverPriority) ? "${str != "" ? "\n" : ""} • Priority: (${settings?.pushoverPriority})" : ""
		str += (settings?.pushoverEnabled && settings?.pushoverSound) ? "${str != "" ? "\n" : ""} • Sound: (${settings?.pushoverSound})" : ""
		str += (settings?.phone) ? "${str != "" ? "\n" : ""}Sending via: (SMS)" : ""
		str += (ap != null) ? "${str != "" ? "\n" : ""}\nEnabled Alerts:\n${ap}" : ""
		str += (nd != null) ? "${str != "" ? "\n" : ""}\nAlert Restrictions:\n${nd}" : ""
	}
	return str != "" ? str : null
}

def getAppNotifDesc() {
	def str = ""
	str += settings?.sendMissedPollMsg != false ? "${str != "" ? "\n" : ""} • Missed Poll Alerts: (${strCapitalize(settings?.sendMissedPollMsg ?: "True")})" : ""
	str += settings?.sendAppUpdateMsg != false ? "${str != "" ? "\n" : ""} • Code Updates: (${strCapitalize(settings?.sendAppUpdateMsg ?: "True")})" : ""
	return str != "" ? str : null
}

def getInputToStringDesc(inpt, addSpace = null) {
	Integer cnt = 0
	String str = ""
	if(inpt) {
		inpt.sort().each { item ->
			cnt = cnt+1
			str += item ? (((cnt < 1) || (inpt?.size() > 1)) ? "\n      ${item}" : "${addSpace ? "      " : ""}${item}") : ""
		}
	}
	//log.debug "str: $str"
	return (str != "") ? "${str}" : null
}

String getNotifSchedDesc() {
	def sun = getSunriseAndSunset()
	def startInput = settings?.qStartInput
	def startTime = settings?.qStartTime
	def stopInput = settings?.qStopInput
	def stopTime = settings?.qStopTime
	def dayInput = settings?.quietDays
	def modeInput = settings?.quietModes
	def notifDesc = ""
	def getNotifTimeStartLbl = ( (startInput == "Sunrise" || startInput == "Sunset") ? ( (startInput == "Sunset") ? epochToTime(sun?.sunset.time) : epochToTime(sun?.sunrise.time) ) : (startTime ? time2Str(startTime) : "") )
	def getNotifTimeStopLbl = ( (stopInput == "Sunrise" || stopInput == "Sunset") ? ( (stopInput == "Sunset") ? epochToTime(sun?.sunset.time) : epochToTime(sun?.sunrise.time) ) : (stopTime ? time2Str(stopTime) : "") )
	notifDesc += (getNotifTimeStartLbl && getNotifTimeStopLbl) ? " • Silent Time: ${getNotifTimeStartLbl} - ${getNotifTimeStopLbl}" : ""
	def days = getInputToStringDesc(dayInput)
	def modes = getInputToStringDesc(modeInput)
	notifDesc += days ? "${(getNotifTimeStartLbl || getNotifTimeStopLbl) ? "\n" : ""} • Silent Day${isPluralString(dayInput)}: ${days}" : ""
	notifDesc += modes ? "${(getNotifTimeStartLbl || getNotifTimeStopLbl || days) ? "\n" : ""} • Silent Mode${isPluralString(modeInput)}: ${modes}" : ""
	return (notifDesc != "") ? "${notifDesc}" : null
}


def debugStatus() { return !settings?.appDebug ? "Off" : "On" }
def deviceDebugStatus() { return !settings?.childDebug ? "Off" : "On" }
def isAppDebug() { return !settings?.appDebug ? false : true }
def isChildDebug() { return !settings?.childDebug ? false : true }

String getAppDebugDesc() {
	def str = ""
	str += isAppDebug() ? "App Debug: (${debugStatus()})" : ""
	str += isChildDebug() && str != "" ? "\n" : ""
	str += isChildDebug() ? "Device Debug: (${deviceDebugStatus()})" : ""
	return (str != "") ? "${str}" : null
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    state?.isInstalled = true
    initialize()
}

def updated() {
    if (state.previousVersion != state.thisSmartAppVersion){
        getVersionInfo(state.previousVersion, state.thisSmartAppVersion);
    }
    state?.isInstalled = true
    unsubscribe()
    initialize()
}

def uninstalled() {
    getVersionInfo(state.previousVersion, 0);
}

def initialize() {
    // listen to LAN incoming messages
    runEvery5Minutes("notificationCheck")
    subscribe(location, null, lanEventHandler, [filterEvents:false])
}

def lanEventHandler(evt) {
    def description = evt.description
    def hub = evt?.hubId
    def msg = parseLanMessage(evt.description)
    //def parsedEvent = parseLanMessage(description)
    def body = msg.body
    def headerMap = msg.headers      // => headers as a Map

    //Filter out calls from other LAN devices
    if (headerMap != null){
        if (headerMap?.source != "STSense" || (headerMap?.senseAppId != null && headerMap?.senseAppId != app?.getId())) {
            //log.debug "Non-sense data detected - ignoring."
            return 0
        }
    }

    Map result = [:]
    if (body != null) {
        try{
            //log.debug body
            def slurper = new groovy.json.JsonSlurper()
            result = slurper.parseText(body)
            //log.debug result
        }
        catch (e){
            log.debug "FYI - got a Sense response, but it's apparently not JSON. Error: " + e + ". Body: " + body
            return 1
        }

        /*
        TODO:
        Populate "availableDevices" map
        If DNI is in "exclude" preference, do not create child
        If "do not rename" preference, do not rename child
        */

        if (result.devices){
            //log.debug result.versionInfo.SmartApp
            Map senseDeviceMap = [:]
            result.devices.each { senseDevice ->
                senseDeviceMap[senseDevice?.id] = senseDevice
                // log.debug "senseDevice(${senseDevice.name}): ${senseDevice}"
                
                //def senseDevice = result.devices[0]
                def dni = [ app.id, (senseDevice != "SenseMonitor" ? "senseDevice" : "senseMonitor"), senseDevice.id].join('|')
                //log.debug "device DNI will be: " + dni + " for " + senseDevice.name
                def childDevice = getChildDevice(dni)
                def childDeviceAttrib = [:]
                def fullName = senseDevice?.id != "SenseMonitor" ? "Sense-" + senseDevice?.name : "Sense-Monitor"
                if (!childDevice){
                    log.debug "name will be: " + fullName
                    //childDeviceAttrib = ["name": senseDevice.name, "completedSetup": true]
                    childDeviceAttrib = ["name": fullName, "completedSetup": true]

                    try{ 
                        if(senseDevice.id == "SenseMonitor") {
                            log.debug "Creating NEW Sense Monitor Device: " + fullName
                        } else { log.debug "Creating NEW Sense Device: " + fullName }
                        childDevice = addChildDevice("brbeaird", "Sense Monitor Device", dni, null, childDeviceAttrib)
                        childDevice.updateDeviceStatus(senseDevice)
                    } catch(physicalgraph.app.exception.UnknownDeviceTypeException e) {
                        log.error "AddDevice Error! ", e
                        //state.installMsg = state.installMsg + deviceName + ": problem creating RM device. Check your IDE to make sure the brbeaird : RainMachine device handler is installed and published. \r\n\r\n"
                    }
                } else {
                    //Check and see if name needs a refresh
                    if (childDevice.name != fullName || childDevice.label != fullName){
                        log.debug ("Updating device name (old label was " + childDevice.label + " old name was " + childDevice.name + " new hotness: " + fullName)
                        childDevice.name = fullName
                        childDevice.label = fullName
                        //state.installMsg = state.installMsg + deviceName + ": updating device name (old name was " + childDevice.label + ") \r\n\r\n"
                    }
                    state?.senseDeviceVersion = childDevice?.devVersion() // Used for the Updater Notifiers
                    
                    childDevice.updateDeviceStatus(senseDevice)
                }
                atomicState?.lastDevDataUpd = getDtNow()
            }
            atomicState?.senseDeviceMap = senseDeviceMap
        }
    }
}

def checkVersionData(now = false) {
	//log.trace "checkVersionData..."
	if (!state?.versionData || (getLastVerUpdSec() > (3600*6))) {
		if(now) {
			getVersionData()
		} else {
			if(canSchedule()) { runIn(45, "getVersionData", [overwrite: true]) }  //This reads a JSON file from a web server with timing values and version numbers
		}
	}
}

Boolean pushStatus() { return (settings?.phone || settings?.usePush || settings?.pushoverEnabled) ? ((settings?.usePush || (settings?.pushoverEnabled && settings?.pushoverDevices)) ? "Push Enabled" : "Enabled") : null }
Integer getLastMsgSec() { return !state?.lastMsgDt ? 100000 : GetTimeDiffSeconds(state?.lastMsgDt, "getLastMsgSec").toInteger() }
Integer getLastUpdMsgSec() { return !state?.lastUpdMsgDt ? 100000 : GetTimeDiffSeconds(state?.lastUpdMsgDt, "getLastUpdMsgSec").toInteger() }
Integer getLastMisPollMsgSec() { return !state?.lastMisPollMsgDt ? 100000 : GetTimeDiffSeconds(state?.lastMisPollMsgDt, "getLastMisPollMsgSec").toInteger() }
Integer getLastVerUpdSec() { return !state?.lastVerUpdDt ? 100000 : GetTimeDiffSeconds(state?.lastVerUpdDt, "getLastVerUpdSec").toInteger() }
Boolean getOk2Notify() { return ((settings?.recipients || settings?.usePush || (settings?.pushoverEnabled && settings?.pushoverDevices)) && (daysOk(settings?.quietDays) && notificationTimeOk() && modesOk(settings?.quietModes))) }
Integer getLastDevicePollSec() { return !state?.lastDevDataUpd ? 840 : GetTimeDiffSeconds(state?.lastDevDataUpd, "getLastDevicePollSec").toInteger() }

Boolean modesOk(modeEntry) {
	def res = true
	if(modeEntry) {
		modeEntry?.each { m ->
			if(m.toString() == location?.mode.toString()) { res = false }
		}
	}
	return res
}

private notificationCheck() {
    checkVersionData()
	if(!getOk2Notify()) { return }
	missPollNotify(settings?.sendMissedPollMsg, (state?.misPollNotifyMsgWaitVal.toInteger() ?: 3600))
	appUpdateNotify()
}

private getVersionInfo(oldVersion, newVersion){
    def params = [
        uri:  'http://www.fantasyaftermath.com/getVersion/sense/' +  oldVersion + '/' + newVersion,
        contentType: 'application/json'
    ]
    asynchttp_v1.get('responseHandlerMethod', params)
}

private getVersionData() {
    def params = [
        uri:  "https://raw.githubusercontent.com/tonesto7/SmartThings_SenseMonitor/master/resources/versions.json",
        contentType: 'application/json'
    ]
    asynchttp_v1.get('versionDataRespHandler', params)
}

private versionDataRespHandler(resp, data) {
    if(resp.hasError()) {
        log.error "versionDataRespHandler Error: " + resp?.errorMessage
    } else {
        if(resp.data) {
            log.info "Getting Latest Version Data from versions.json File..."
            state?.versionData = resp?.data
            state?.lastVerUpdDt = getDtNow()
        }
        log.trace ("versionDataRespHandler Resp: ${resp?.data}")
    }
}

def responseHandlerMethod(response, data) {
    if (response.hasError()) {
        log.error "response has error: $response.errorMessage"
    } else {
        def results = response.json
        state.latestSmartAppVersion = results.SmartApp;
        state.latestDeviceVersion = results.DoorDevice;
    }

    log.debug "previousVersion: " + state.previousVersion
    log.debug "installedVersion: " + state.thisSmartAppVersion
    log.debug "latestVersion: " + state.latestSmartAppVersion
    log.debug "deviceVersion: " + state.latestDeviceVersion
}


def versionCheck(){
    state.versionWarning = ""
    state.thisDeviceVersion = ""

    def childExists = false
    def childDevs = getChildDevices()

    if (childDevs.size() > 0){
        childExists = true
        state.thisDeviceVersion = childDevs[0].showVersion()
        log.debug "child version found: " + state.thisDeviceVersion
    }

    log.debug "RM Device Handler Version: " + state.thisDeviceVersion

    if (state.thisSmartAppVersion != state.latestSmartAppVersion) {
        state.versionWarning = state.versionWarning + "Your SmartApp version (" + state.thisSmartAppVersion + ") is not the latest version (" + state.latestSmartAppVersion + ")\n\n"
    }
    if (childExists && state.thisDeviceVersion != state.latestDeviceVersion) {
        state.versionWarning = state.versionWarning + "Your RainMachine device version (" + state.thisDeviceVersion + ") is not the latest version (" + state.latestDeviceVersion + ")\n\n"
    }

    log.debug state.versionWarning
}

private missPollNotify(on, wait) {
	if(!on || !wait || !(getLastDevicePollSec() > (state?.misPollNotifyWaitVal.toInteger() ?: 900))) { return }
	def missedPoll = (getLastMisPollMsgSec() > wait.toInteger()) ? true : true
	if(!on || !wait || !missedPoll) { return }
	if(on && missedPoll) {
		def msg = "\nThe app has not refreshed energy data in the last (${getLastDevicePollSec()}) seconds.\nPlease try refreshing data using device refresh button."
		log.warn msg.toString().replaceAll("\n", " ")
		if(sendMsg("${app.name} Polling Issue", msg)) {
			state?.lastMisPollMsgDt = getDtNow()
		}
	}
}

private appUpdateNotify() {
	def on = settings?.app
	def appUpd = isAppUpdateAvail()
	def devUpd = isDevUpdateAvail()
	if(getLastUpdMsgSec() > state?.updNotifyWaitVal.toInteger()) {
		if(appUpd || devUpd) {
			def str = ""
			str += !appUpd ? "" : "Sense App: v${state?.versionData?.updater?.versions?.mainApp?.ver?.toString()}"
			str += !devUpd ? "" : "\nMonitor Device: v${state?.versionData?.updater?.versions?.senseDevice?.ver?.toString()}"
			sendMsg("Info", "Sense Monitor Update(s) are Available:${str}...\n\nPlease visit the IDE to Update your code...")
			state?.lastUpdMsgDt = getDtNow()
		}
	}
}

Boolean isCodeUpdateAvailable(String newVer, String curVer, String type) {
	Boolean result = false
	def latestVer
	if(newVer && curVer) {
		List versions = [newVer, curVer]
		if(newVer != curVer) {
			latestVer = versions?.max { a, b ->
				List verA = a?.tokenize('.')
				List verB = b?.tokenize('.')
				Integer commonIndices = Math.min(verA?.size(), verB?.size())
				for (int i = 0; i < commonIndices; ++i) {
					//log.debug "comparing $numA and $numB"
					if(verA[i]?.toInteger() != verB[i]?.toInteger()) {
						return verA[i]?.toInteger() <=> verB[i]?.toInteger()
					}
				}
				verA?.size() <=> verB?.size()
			}
			result = (latestVer == newVer) ? true : false
		}
	}
	// log.trace ("type: $type | newVer: $newVer | curVer: $curVer | newestVersion: ${latestVer} | result: $result")
	return result
}

Boolean isAppUpdateAvail() {
	if(isCodeUpdateAvailable(state?.versionData?.updater?.versions?.mainApp?.ver, appVer(), "manager")) { return true }
	return false
}

Boolean isDevUpdateAvail() {
	if(isCodeUpdateAvailable(state?.versionData?.updater?.versions?.senseDevice?.ver, atomicState?.devVer, "dev")) { return true }
	return false
}

def formatDt(dt, tzChg=true) {
	def tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	if(tzChg) { if(location.timeZone) { tf.setTimeZone(location?.timeZone) } }
	return tf?.format(dt)
}

String strCapitalize(str) { return str ? str?.toString().capitalize() : null }
String isPluralString(obj) { return (obj?.size() > 1) ? "(s)" : "" }

def parseDt(pFormat, dt, tzFmt=true) {
	def result
	def newDt = Date.parse("$pFormat", dt)
	result = formatDt(newDt, tzFmt)
	//log.debug "parseDt Result: $result"
	return result
}

def getDtNow() {
	def now = new Date()
	return formatDt(now)
}

def GetTimeDiffSeconds(lastDate, sender=null) {
	try {
		if(lastDate?.contains("dtNow")) { return 10000 }
		def now = new Date()
		def lastDt = Date.parse("E MMM dd HH:mm:ss z yyyy", lastDate)
		def start = Date.parse("E MMM dd HH:mm:ss z yyyy", formatDt(lastDt)).getTime()
		def stop = Date.parse("E MMM dd HH:mm:ss z yyyy", formatDt(now)).getTime()
		def diff = (int) (long) (stop - start) / 1000
		return diff
	}
	catch (ex) {
		log.error "GetTimeDiffSeconds Exception: (${sender ? "$sender | " : ""}lastDate: $lastDate):", ex
		return 10000
	}
}

public sendMsg(String msgType, String msg, Boolean showEvt=true, Map pushoverMap=null, sms=null, push=null) {
	//log.trace("sendMsg:  msgType: ${msgType}, msg: ${msg}, showEvt: ${showEvt}")
	log.trace ("sendMsg")
	def sentstr = "Push"
	def sent = false
	try {
		def newMsg = "${msgType}: ${msg}" as String
		def flatMsg = newMsg.toString().replaceAll("\n", " ")
		if(!getOk2Notify()) {
			LogAction("sendMsg: Message Skipped During Quiet Time ($flatMsg)", "info", true)
			if(showEvt) { sendNotificationEvent(newMsg) }
		} else {
			
            if(push || settings?.usePush) {
                sentstr = "Push Message"
                if(showEvt) {
                    sendPush(newMsg)	// sends push and notification feed
                } else {
                    sendPushMessage(newMsg)	// sends push
                }
                sent = true
            }
            if(settings?.pushoverEnabled && settings?.pushoverDevices) {
                sentstr = "Pushover Message"
                Map msgObj = [:]
                msgObj = pushoverMap ?: [title: msgType, message: msg, priority: (settings?.pushoverPriority?:0)]
                if(settings?.pushoverSound) { msgObj?.sound = settings?.pushoverSound }
                buildPushMessage(settings?.pushoverDevices, msgObj, true)
                sent = true
            }
            def thephone = sms ? sms.toString() : settings?.phone ? settings?.phone?.toString() : ""
            if(thephone) {
                sentstr = "Text Message to Phone [${thephone}]"
                def t0 = newMsg.take(140)
                if(showEvt) {
                    sendSms(thephone as String, t0 as String)	// send SMS and notification feed
                } else {
                    sendSmsMessage(thephone as String, t0 as String)	// send SMS
                }
                sent = true
            }
			if(sent) {
				atomicState?.lastMsg = flatMsg
				atomicState?.lastMsgDt = getDtNow()
				LogAction("sendMsg: Sent ${sentstr} (${flatMsg})", "debug", true)
			}
		}
	} catch (ex) {
		log.error "sendMsg $sentstr Exception:", ex
	}
	return sent
}

//PushOver-Manager Input Generation Functions
private getPushoverSounds(){return (Map) atomicState?.pushoverManager?.sounds?:[:]}
private getPushoverDevices(){List opts=[];Map pmd=atomicState?.pushoverManager?:[:];pmd?.apps?.each{k,v->if(v&&v?.devices&&v?.appId){Map dm=[:];v?.devices?.sort{}?.each{i->dm["${i}_${v?.appId}"]=i};addInputGrp(opts,v?.appName,dm);}};return opts;}
private inputOptGrp(List groups,String title){def group=[values:[],order:groups?.size()];group?.title=title?:"";groups<<group;return groups;}
private addInputValues(List groups,String key,String value){def lg=groups[-1];lg["values"]<<[key:key,value:value,order:lg["values"]?.size()];return groups;}
private listToMap(List original){original.inject([:]){r,v->r[v]=v;return r;}}
private addInputGrp(List groups,String title,values){if(values instanceof List){values=listToMap(values)};values.inject(inputOptGrp(groups,title)){r,k,v->return addInputValues(r,k,v)};return groups;}
private addInputGrp(values){addInputGrp([],null,values)}
//PushOver-Manager Location Event Subscription Events, Polling, and Handlers
public pushover_init(){subscribe(location,"pushoverManager",pushover_handler);pushover_poll()}
public pushover_cleanup(){state?.remove("pushoverManager");unsubscribe("pushoverManager");}
public pushover_poll(){sendLocationEvent(name:"pushoverManagerCmd",value:"poll",data:[empty:true],isStateChange:true,descriptionText:"Sending Poll Event to Pushover-Manager")}
public pushover_msg(List devs,Map data){if(devs&&data){sendLocationEvent(name:"pushoverManagerMsg",value:"sendMsg",data:data,isStateChange:true,descriptionText:"Sending Message to Pushover Devices: ${devs}");}}
public pushover_handler(evt){Map pmd=atomicState?.pushoverManager?:[:];switch(evt?.value){case"refresh":def ed = evt?.jsonData;String id = ed?.appId;Map pA = pmd?.apps?.size() ? pmd?.apps : [:];if(id){pA[id]=pA?."${id}"instanceof Map?pA[id]:[:];pA[id]?.devices=ed?.devices?:[];pA[id]?.appName=ed?.appName;pA[id]?.appId=id;pmd?.apps = pA;};pmd?.sounds=ed?.sounds;break;case "reset":pmd=[:];break;};atomicState?.pushoverManager=pmd;}
//Builds Map Message object to send to Pushover Manager
private buildPushMessage(List devices,Map msgData,timeStamp=false){if(!devices||!msgData){return};Map data=[:];data?.appId=app?.getId();data.devices=devices;data?.msgData=msgData;if(timeStamp){data?.msgData?.timeStamp=new Date().getTime()};pushover_msg(devices,data);}