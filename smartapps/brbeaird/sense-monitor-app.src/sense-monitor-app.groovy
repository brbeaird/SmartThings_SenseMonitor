/**
 *	Sense Monitor SmartApp
 *
 *	Author: Brian Beaird and Anthony Santilli
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
include 'asynchttp_v1'

String appVersion() { return "0.3.4" }
String appModified() { return "2019-04-04"}
String appAuthor() { return "Anthony Santilli & Brian Beaird" }
String gitBranch() { return "brbeaird" }
String getAppImg(imgName) 	{ return "https://raw.githubusercontent.com/${gitBranch()}/SmartThings_SenseMonitor/master/resources/icons/$imgName" }
Map minVersions() { //These define the minimum versions of code this app will work with.
	return [
		monitorDevice: 033,
		energyDevice: 033,
		server: 040
	]
}

definition(
	name: "Sense Monitor App",
	namespace: "brbeaird",
	author: "Anthony Santilli & Brian Beaird",
	description: "Connects SmartThings with Sense",
	category: "My Apps",
	iconUrl: "https://raw.githubusercontent.com/${gitBranch()}/SmartThings_SenseMonitor/master/resources/icons/sense.1x.png",
	iconX2Url: "https://raw.githubusercontent.com/${gitBranch()}/SmartThings_SenseMonitor/master/resources/icons/sense.2x.png",
	iconX3Url: "https://raw.githubusercontent.com/${gitBranch()}/SmartThings_SenseMonitor/master/resources/icons/sense.3x.png")


preferences {
	page(name: "mainPage")
	page(name: "newSetupPage")
	page(name: "notifPrefPage")
	page(name: "servPrefPage")
	page(name: "setNotificationTimePage")
	page(name: "uninstallPage")
}

def appInfoSect(sect=true)	{
	def str = ""
	str += "${app?.name}"
	str += "\nAuthor: ${appAuthor()}"
	str += "\nVersion: ${appVersion()}"
	section() { paragraph str, image: getAppImg("sense.2x.png") }
}

def mainPage() {
	checkVersionData(true)
	Boolean newInstall = !state?.isInstalled
	dynamicPage(name: "mainPage", nextPage: (!newInstall ? "" : "servPrefPage"), uninstall: false, install: !newInstall) {
		appInfoSect()
		section("Device Preferences:") {
			if(!newInstall) {
				List devs = getDeviceList()?.collect { "${it?.value?.name} (${it?.value?.usage ?: 0} W)" }?.sort()
				paragraph title: "Discovered Devices:", "${devs?.size() ? devs?.join("\n") : "No Devices Available"}"
			}
			input "autoCreateDevices", "bool", title: "Auto Create New Devices?", description: "", required: false, defaultValue: true, submitOnChange: true, image: getAppImg("devices.png")
			input "autoRenameDevices", "bool", title: "Rename Devices to Match Sense?", description: "", required: false, defaultValue: true, submitOnChange: true, image: getAppImg("name_tag.png")
		}

		section("Device Filtering:") {
			if(newInstall) {
				paragraph title:"Notice:", "Device filtering options will be available once app install is complete.", required: true, state: null
			} else {
				Map devs = getDeviceList(true, false)
				input "senseDeviceFilter", "enum", title: "Don't Use these Devices", description: "Tap to select", options: (devs ? devs?.sort{it?.value} : []), multiple: true, required: false, submitOnChange: true, image: getAppImg("exclude.png")
				paragraph title:"Notice:", "Any sense devices created by this app will require manual removal, or uninstall the app to remove all devices!"
			}
		}
		if(!newInstall) {
			section("Sense Service:") {
				def t0 = getServiceConfDesc()
				href "servPrefPage", title: "Sense Service\nSettings", description: (t0 ? "${t0}\n\nTap to modify" : "Tap to configure"), state: (t0 ? "complete" : null), image: getAppImg("settings.png")
			}
		}
		section("Notifications:") {
			def t0 = getAppNotifConfDesc()
			href "notifPrefPage", title: "App and Device\nNotifications", description: (t0 ? "${t0}\n\nTap to modify" : "Tap to configure"), state: (t0 ? "complete" : null), image: getAppImg("notification2.png")
		}
		section ("Application Logs") {
			input (name: "appDebug", type: "bool", title: "Show App Logs in the IDE?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("debug.png"))
		}
		if(!newInstall) {
			section("") {
				href "uninstallPage", title: "Uninstall this App", description: "Tap to Remove...", image: getAppImg("uninstall.png")
			}
		}
	}
}

Map getDeviceList(isInputEnum=false, hideDefaults=true) {
	Map devMap = [:]
	Map availDevs = state?.senseDeviceMap ?: [:]
	availDevs?.each { key, val->
		if(hideDefaults) {
			if(!(key?.toString() in ["TotalUsage", "unknown", "SenseMonitor", "always_on"])) {
				devMap[key] = val
			}
		} else { devMap[key] = val }
	}
	return isInputEnum ? (devMap?.size() ? devMap?.collectEntries { [(it?.key):it?.value?.name] } : devMap) : devMap
}

def servPrefPage() {
	Boolean newInstall = !state?.isInstalled
	dynamicPage(name: "servPrefPage", install: newInstall) {
		if(newInstall) {
			section("") {
				paragraph "Please configure the options below before completing the App install", state: "complete"
			}
		}
		section("Hub Selection:") {
			input(name: "stHub", type: "hub", title: "Select Local Hub", description: "IP adress changes will be sent to service.", required: true, submitOnChange: true, image: getAppImg("hub.png"))
		}
		if(settings?.stHub) {
			section("Service Push Settings") {
				input (name: "websocketPollingInterval", type: "number", title: "Time between websocket polling", description: "in Seconds...", required: false, defaultValue: 60, submitOnChange: true, image: getAppImg("delay_time.png"))
				input (name: "refreshInterval", type: "number", title: "Time between monitor data refreshes", description: "in Seconds...", required: false, defaultValue: 300, submitOnChange: true, image: getAppImg("delay_time.png"))
				paragraph title: "Notice", "Setting these lower than the default may trigger Sense rate limiting. These changes will be applied on the next server data refresh."
			}
		}
		if(!newInstall && state?.nodeServiceInfo) {
			section() {
				paragraph title: "Service Info:", getServInfoDesc(), state: "complete"
			}
		}
	}
}

def notifPrefPage() {
	dynamicPage(name: "notifPrefPage", install: false) {
		Integer pollWait = 900
		Integer pollMsgWait = 3600
		Integer updNotifyWait = 7200
		section("") {
			paragraph title: "Notice:", "The settings configure here are used by both the App and the Devices.", state: "complete"
		}
		section("Push Messages:") {
			input "usePush", "bool", title: "Send Push Notitifications\n(Optional)", required: false, submitOnChange: true, defaultValue: false, image: getAppImg("notification.png")
		}
		section("SMS Text Messaging:") {
			paragraph "To send to multiple numbers separate the number by a comma\nE.g. 8045551122,8046663344"
			input "smsNumbers", "text", title: "Send SMS to Text to...\n(Optional)", required: false, submitOnChange: true, image: getAppImg("sms_phone.png")
		}
		section("Pushover Support:") {
			input ("pushoverEnabled", "bool", title: "Use Pushover Integration", required: false, submitOnChange: true, image: getAppImg("pushover.png"))
			if(settings?.pushoverEnabled == true) {
				if(state?.isInstalled) {
					if(!state?.pushoverManager) {
						paragraph "If this is the first time enabling Pushover than leave this page and come back if the devices list is empty"
						pushover_init()
					} else {
						input "pushoverDevices", "enum", title: "Select Pushover Devices", description: "Tap to select", groupedOptions: getPushoverDevices(), multiple: true, required: false, submitOnChange: true
						if(settings?.pushoverDevices) {
							def t0 = ["-2":"Lowest", "-1":"Low", "0":"Normal", "1":"High", "2":"Emergency"]
							input "pushoverPriority", "enum", title: "Notification Priority (Optional)", description: "Tap to select", defaultValue: "0", required: false, multiple: false, submitOnChange: true, options: t0
							input "pushoverSound", "enum", title: "Notification Sound (Optional)", description: "Tap to select", defaultValue: "pushover", required: false, multiple: false, submitOnChange: true, options: getPushoverSounds()
						}
					}
				} else { paragraph "New Install Detected!!!\n\n1. Press Done to Finish the Install.\n2. Goto the Automations Tab at the Bottom\n3. Tap on the SmartApps Tab above\n4. Select ${app?.getLabel()} and Resume configuration", state: "complete" }
			}
		}
		if(settings?.smsNumbers?.toString()?.length()>=10 || settings?.usePush || (settings?.pushoverEnabled && settings?.pushoverDevices)) {
			if((settings?.usePush || (settings?.pushoverEnabled && settings?.pushoverDevices)) && !state?.pushTested && state?.pushoverManager) {
				if(sendMsg("Info", "Push Notification Test Successful. Notifications Enabled for ${app?.label}", true)) {
					state.pushTested = true
				}
			}
			section("Notification Restrictions:") {
				def t1 = getNotifSchedDesc()
				href "setNotificationTimePage", title: "Notification Restrictions", description: (t1 ?: "Tap to configure"), state: (t1 ? "complete" : null), image: getAppImg("restriction.png")
			}
			section("Missed Poll Alerts:") {
				input (name: "sendMissedPollMsg", type: "bool", title: "Send Missed Checkin Alerts?", defaultValue: true, submitOnChange: true, image: getAppImg("late.png"))
				if(settings?.sendMissedPollMsg) {
					def misPollNotifyWaitValDesc = settings?.misPollNotifyWaitVal ?: "Default: 15 Minutes"
					input (name: "misPollNotifyWaitVal", type: "enum", title: "Time Past the Missed Checkin?", required: false, defaultValue: 900, metadata: [values:notifValEnum()], submitOnChange: true, image: getAppImg("delay_time.png"))
					if(settings?.misPollNotifyWaitVal) { pollWait = settings?.misPollNotifyWaitVal as Integer }

					def misPollNotifyMsgWaitValDesc = settings?.misPollNotifyMsgWaitVal ?: "Default: 1 Hour"
					input (name: "misPollNotifyMsgWaitVal", type: "enum", title: "Send Reminder After?", required: false, defaultValue: 3600, metadata: [values:notifValEnum()], submitOnChange: true, image: getAppImg("reminder.png"))
					if(settings?.misPollNotifyMsgWaitVal) { pollMsgWait = settings?.misPollNotifyMsgWaitVal as Integer }
				}
			}
			section("Code Update Alerts:") {
				input (name: "sendAppUpdateMsg", type: "bool", title: "Send for Updates...", defaultValue: true, submitOnChange: true, image: getAppImg("update.png"))
				if(settings?.sendAppUpdateMsg) {
					def updNotifyWaitValDesc = settings?.updNotifyWaitVal ?: "Default: 2 Hours"
					input (name: "updNotifyWaitVal", type: "enum", title: "Send Reminders After?", required: false, defaultValue: 7200, metadata: [values:notifValEnum()], submitOnChange: true, image: getAppImg("reminder.png"))
					if(settings?.updNotifyWaitVal) { updNotifyWait = settings?.updNotifyWaitVal as Integer }
				}
			}
		} else { state.pushTested = false }
		state.misPollNotifyWaitVal = pollWait
		state.misPollNotifyMsgWaitVal = pollMsgWait
		state.updNotifyWaitVal = updNotifyWait
	}
}

def setNotificationTimePage() {
	dynamicPage(name: "setNotificationTimePage", title: "Prevent Notifications\nDuring these Days, Times or Modes", uninstall: false) {
		def timeReq = (settings["qStartTime"] || settings["qStopTime"]) ? true : false
		section() {
			input "qStartInput", "enum", title: "Starting at", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: null, submitOnChange: true, required: false, image: getAppImg("start_time.png")
			if(settings["qStartInput"] == "A specific time") {
				input "qStartTime", "time", title: "Start time", required: timeReq, image: getAppImg("start_time.png")
			}
			input "qStopInput", "enum", title: "Stopping at", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: null, submitOnChange: true, required: false, image: getAppImg("stop_time.png")
			if(settings?."qStopInput" == "A specific time") {
				input "qStopTime", "time", title: "Stop time", required: timeReq, image: getAppImg("stop_time.png")
			}
			input "quietDays", "enum", title: "Only on these days of the week", multiple: true, required: false, image: getAppImg("day_calendar.png"),
					options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
			input "quietModes", "mode", title: "When these Modes are Active", multiple: true, submitOnChange: true, required: false, image: getAppImg("mode.png")
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

def installed() {
	log.debug "Installed with settings: ${settings}"
	state?.isInstalled = true
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	if(!state?.isInstalled) { state?.isInstalled = true }
	unsubscribe()
	initialize()
}

def uninstalled() {
	log.warn "uninstalling app and devices"
}

def initialize() {
	// listen to LAN incoming messages
	if(app?.getLabel() != "Sense Monitor App") { app?.updateLabel("Sense Monitor App") }
	runEvery5Minutes("notificationCheck") // This task checks for missed polls and app updates
	subscribe(app, onAppTouch)
	subscribe(location, null, lanEventHandler, [filterEvents:false])
	stateCleanup()
	updCodeVerMap()
	runIn(5, "senseServiceUpdate")
	runIn(3, "reInitDevices")
}

private checkIfCodeUpdated() {
	if(state?.codeVersions && state?.codeVersions?.mainApp != appVersion()) {
		log.info "Code Version Change! Re-Initializing SmartApp in 5 seconds..."
		state?.pollBlocked = true
		runIn(5, "updated", [overwrite: false])
		return true
	}
	state?.pollBlocked = false
	return false
}

private stateCleanup() {
	List items = ["availableDevices", "lastMsgDt"]
	items?.each { si-> if(state?.containsKey(si as String)) { state?.remove(si)} }
	state?.pollBlocked = false
}

def onAppTouch(evt) {
	log.trace "appTouch..."
	updated()
}

private updCodeVerMap() {
	Map cv = state?.codeVersions ?: [:]
	cv["mainApp"] = appVersion()
	state?.codeVersions = cv
}

private modCodeVerMap(key, val) {
	Map cv = state?.codeVersions ?: [:]
	cv["$key"] = val
	state?.codeVersions = cv
}

private reInitDevices() {
	getChildDevices(true)?.each { cd-> cd?.initialize() }
}

def lanEventHandler(evt) {
	def msg
    try{
            msg = parseLanMessage(evt.description)
    }
    catch (e){
        	//log.debug "Not able to parse lan message: " + e
            return 1
    }
	def headerMap = msg?.headers
	//Filter out calls from other LAN devices
	if (headerMap != null){
		if (headerMap?.source != "STSense") {
			// log.debug "Non-sense data detected - ignoring."
			return 0
		}
		if (headerMap?.source == "STSense" && headerMap?.senseAppId && headerMap?.senseAppId?.toString() != app?.getId()) {
			log.warn "STSense Data Recieved but it was meant for a different SmartAppId..."
			return 0
		}
	}

	Map result = [:]
	if (msg?.body != null) {
		try {
			def slurper = new groovy.json.JsonSlurper()
			result = slurper?.parseText(msg?.body) as Map
		} catch (e) {
			log.debug "FYI - got a Sense response, but it's apparently not JSON. Error: " + e + ". Body: " + msg?.body
			return 1
		}
		if(checkIfCodeUpdated()) {
			log.warn "Possible Code Version Update Detected... Device Updates will occur on next cycle."
			return 0
		}
		//Check for minimum versions before processing
		Boolean updRequired = false
		List updRequiredItems = []
		["server":"Sense Server", "monitorDevice":"Monitor Device", "energyDevice":"Energy Device"]?.each { k,v->
			Map codeVers = state?.codeVersions
			if(codeVers && codeVers[k as String] && (versionStr2Int(codeVers[k as String]) < minVersions()[k as String])) { updRequired = true; updRequiredItems?.push("$v"); }
		}

        //When device ID array received, audit child devices to see if we should delete any stale children
		if (result?.deviceIds) {
        	getAllChildDevices().each { child ->
                def devId =  child.deviceNetworkId.split("\\|")[2]
                if (devId != "SenseMonitor" && !result.deviceIds.contains(devId)){
                	log.debug "Found possible stale Sense device. Checking for recent events: ${child.label} (${devId})"
                    def eventCount = 0
                    def recentEvents = child.eventsSince(new Date() - 7)
                    recentEvents.each { event ->
                    	if (event.name != "DeviceWatch-DeviceStatus"){eventCount++}
                    }
                    if (eventCount == 0){
                    	log.debug "No recent events found. Deleting ${child.label} (${devId})"
                        deleteChildDevice(child.deviceNetworkId, true)
					}
                }
            }
		}

		//When toggleDevices array received, toggle them on and off just to have a record of the event. This happens when devices turn on and off between polling intervals
		if (result?.toggleIds) {
        	result.toggleIds.each { toggleDevice ->
            	def dni = [ app?.id, "senseDevice", toggleDevice].join('|')
                log.debug "toggling " + dni
                def childDevice = getChildDevice(dni)
                childDevice?.toggleOn()
                childDevice?.toggleOff()
            }
		}

		List ignoreTheseDevs = settings?.senseDeviceFilter ?: []
		if (result?.devices) {
			Map senseDeviceMap = [:]
            if (state.senseDeviceMap){senseDeviceMap = state.senseDeviceMap}             
            //Map senseDeviceMap = state.senseDeviceMap
			log.debug "Updating (${result?.devices?.size()}) Sense Devices..."
			result?.devices?.each { senseDevice ->
				Boolean isMonitor = (senseDevice?.id == "SenseMonitor")
				senseDeviceMap[senseDevice?.id] = senseDevice
				// log.debug "senseDevice(${senseDevice.name}): ${senseDevice}"
				if(senseDevice?.id in ignoreTheseDevs) {
					logger("warn", "skipping ${senseDevice?.name} because it is in the do not use list...")
					return
				}
				// logger("debug", "${senseDevice.name} | State: (${senseDevice?.state?.toString().toUpperCase()}) | Usage: ${senseDevice?.usage}W")
				def dni = [ app?.id, (!isMonitor ? "senseDevice" : "senseMonitor"), senseDevice?.id].join('|')
				//log.debug "device DNI will be: " + dni + " for " + senseDevice.name
				def childDevice = getChildDevice(dni)
				// log.debug "childHandlerName: ${childDevice?.name}"
				Map childDeviceAttrib = [:]
				String fullName = !isMonitor ? "Sense-" + senseDevice?.name : "Sense Monitor"
				// log.debug "childDeviceLabel: ${childDevice?.getLabel()} | senseName: ${senseDevice?.name}"
				String childHandlerName = isMonitor ? "Sense Monitor Device" : "Sense Energy Device"
				if(!updRequired) {
					if (!childDevice) {
						log.debug "name will be: " + fullName
						childDeviceAttrib = [name: childHandlerName, label: fullName, completedSetup: true]
						try{
							if(isMonitor) {
								log.debug "Creating NEW Sense Monitor Device: " + fullName
								childDevice = addChildDevice("brbeaird", "Sense Monitor Device", dni, null, childDeviceAttrib)
							} else {
								log.debug "Creating NEW Sense Energy Device: " + fullName
								childDevice = addChildDevice("brbeaird", "Sense Energy Device", dni, null, childDeviceAttrib)
							}
						} catch(physicalgraph.app.exception.UnknownDeviceTypeException ex) {
							log.error "AddDevice Error! ", ex
						}
					} else {
						//Check and see if name needs a refresh
						if (settings?.autoRenameDevices != false && (childDevice?.name != childHandlerName || childDevice?.label != fullName)) {
                            log.debug ("Updating device name (old label was " + childDevice?.label + " | old name was " + childDevice?.name + " new name: " + fullName)
							childDevice?.name = childHandlerName
							childDevice?.label = fullName
						}
					}
					childDevice?.updateDeviceStatus(senseDevice)
				}
				// log.debug "------"
				modCodeVerMap((isMonitor ? "monitorDevice" : "energyDevice"), childDevice?.devVersion()) // Update device versions in codeVersion state Map
				state?.lastDevDataUpd = getDtNow()

			}
			state?.senseDeviceMap = senseDeviceMap
		}
		if(result?.serviceInfo) {
			Map srvcInfo = result?.serviceInfo
			state?.nodeServiceInfo = srvcInfo
			Boolean sendSetUpd = false
			if(srvcInfo?.config && srvcInfo?.config?.size()) {
				srvcInfo?.config?.each { k,v->
					if(settings?.containsKey(k as String)) {
						if(settings[k as String] != v) {
							sendSetUpd = true
							log.debug "config($k) | Service: $v | App: ${settings[k as String]} | result: ${(srvVal != appVal)} | sendUpdate: ${sendSetUpd}"
						}
					}
				}
			}
			modCodeVerMap("server", srvcInfo?.version)
			if(sendSetUpd) { senseServiceUpdate() }
		}
		if(updRequired) {
			log.error "CODE UPDATES REQUIRED:  Sense Monitor Integration will not function until the following items are ALL Updated ${updRequiredItems}..."
			appUpdateNotify()
		}
	}
	return 0
}

private senseServiceUpdate() {
	// log.trace("senseServiceUpdate")
	String ip = state?.nodeServiceInfo?.ip
	String port = state?.nodeServiceInfo?.port
	String host = ip && port ? "${ip}:${port}" : null
	String smartThingsHubIp = settings?.stHub?.getLocalIP()
	if(!host) { return }

	logger("trace", "senseServiceUpdate host: ${host}")
	try {
		def hubAction = new physicalgraph.device.HubAction(
			method: "POST",
			headers: [
				"HOST": host,
				"smartThingsHubIp": "${smartThingsHubIp}",
				"refreshInterval": settings?.refreshInterval,
				"websocketPollingInterval": settings?.websocketPollingInterval
			],
			path: "/updateSettings",
			body: ""
		)
		sendHubCommand(hubAction)
	}
	catch (Exception e) {
		log.error "senseServiceUpdate Exception $e on $hubAction"
	}
}

/******************************************
|    Notification Functions
*******************************************/
Map notifValEnum(allowCust = true) {
	Map items = [
		300:"5 Minutes", 600:"10 Minutes", 900:"15 Minutes", 1200:"20 Minutes", 1500:"25 Minutes",
		1800:"30 Minutes", 3600:"1 Hour", 7200:"2 Hours", 14400:"4 Hours", 21600:"6 Hours", 43200:"12 Hours", 86400:"24 Hours"
	]
	if(allowCust) { items[100000] = "Custom" }
	return items
}

private notificationCheck() {
	// logger("trace", "notificationCheck")
	checkVersionData()
	if(!getOk2Notify()) { return }
	missPollNotify((settings?.sendMissedPollMsg == true), (state?.misPollNotifyMsgWaitVal ?: 3600))
	appUpdateNotify()
}

private missPollNotify(Boolean on, Integer wait) {
	logger("trace", "missPollNotify() | on: ($on) | wait: ($wait) | getLastDevicePollSec: (${getLastDevicePollSec()}) | misPollNotifyWaitVal: (${state?.misPollNotifyWaitVal}) | getLastMisPollMsgSec: (${getLastMisPollMsgSec()})")
	if(!on || !wait || !(getLastDevicePollSec() > (state?.misPollNotifyWaitVal ?: 900))) { return }
	if(!(getLastMisPollMsgSec() > wait.toInteger())) {
		return
	} else {
		def msg = "\nThe app has not received any device data from Sense service in the last (${getLastDevicePollSec()}) seconds.\nSomething must be wrong with the node server."
		log.warn "${msg.toString().replaceAll("\n", " ")}"
		if(sendMsg("${app.name} Data Refresh Issue", msg)) {
			state?.lastMisPollMsgDt = getDtNow()
		}
		getChildDevices(true)?.each { cd-> cd?.sendEvent(name: "DeviceWatch-DeviceStatus", value: "offline", displayed: true, isStateChange: true) }
	}
}

private appUpdateNotify() {
	Boolean on = (settings?.sendAppUpdateMsg != false)
	Boolean appUpd = isAppUpdateAvail()
	Boolean monDevUpd = isMonitorDevUpdateAvail()
	Boolean enerDevUpd = isEnergyDevUpdateAvail()
	Boolean servUpd = isServerUpdateAvail()
	// logger("trace", "appUpdateNotify() | on: (${on}) | appUpd: (${appUpd}) | monDevUpd: (${monDevUpd}) | enerDevUpd: (${enerDevUpd}) | servUpd: (${servUpd}) | getLastUpdMsgSec: ${getLastUpdMsgSec()} | state?.updNotifyWaitVal: ${state?.updNotifyWaitVal}")
	if(getLastUpdMsgSec() > state?.updNotifyWaitVal.toInteger()) {
		if(appUpd || monDevUpd || enerDevUpd || servUpd) {
			def str = ""
			str += !appUpd ? "" : "${str == "" ? "" : "\n"}Sense Monitor App: v${state?.versionData?.versions?.mainApp?.ver?.toString()}"
			str += !monDevUpd ? "" : "${str == "" ? "" : "\n"}Sense Monitor Device: v${state?.versionData?.versions?.monitorDevice?.ver?.toString()}"
			str += !enerDevUpd ? "" : "${str == "" ? "" : "\n"}Sense Energy Device: v${state?.versionData?.versions?.energyDevice?.ver?.toString()}"
			str += !servUpd ? "" : "${str == "" ? "" : "\n"}Sense Node Server: v${state?.versionData?.versions?.server?.ver?.toString()}"
			sendMsg("Info", "Sense Monitor Update(s) are Available:${str}...\n\nPlease visit the IDE to Update your code...")
			state?.lastUpdMsgDt = getDtNow()
		}
	}
}

Boolean pushStatus() { return (settings?.smsNumbers?.toString()?.length()>=10 || settings?.usePush || settings?.pushoverEnabled) ? ((settings?.usePush || (settings?.pushoverEnabled && settings?.pushoverDevices)) ? "Push Enabled" : "Enabled") : null }
Integer getLastMsgSec() { return !state?.lastMsgDt ? 100000 : GetTimeDiffSeconds(state?.lastMsgDt, "getLastMsgSec").toInteger() }
Integer getLastUpdMsgSec() { return !state?.lastUpdMsgDt ? 100000 : GetTimeDiffSeconds(state?.lastUpdMsgDt, "getLastUpdMsgSec").toInteger() }
Integer getLastMisPollMsgSec() { return !state?.lastMisPollMsgDt ? 100000 : GetTimeDiffSeconds(state?.lastMisPollMsgDt, "getLastMisPollMsgSec").toInteger() }
Integer getLastVerUpdSec() { return !state?.lastVerUpdDt ? 100000 : GetTimeDiffSeconds(state?.lastVerUpdDt, "getLastVerUpdSec").toInteger() }
Integer getLastDevicePollSec() { return !state?.lastDevDataUpd ? 840 : GetTimeDiffSeconds(state?.lastDevDataUpd, "getLastDevicePollSec").toInteger() }
Boolean getOk2Notify() { return ((settings?.smsNumbers?.toString()?.length()>=10 || settings?.usePush || (settings?.pushoverEnabled && settings?.pushoverDevices)) && (quietDaysOk(settings?.quietDays) && quietTimeOk() && quietModesOk(settings?.quietModes))) }

Boolean quietModesOk(List modes) {
	if(modes) { return (location?.mode?.toString() in modes) ? false : true }
	return true
}

Boolean quietTimeOk() {
	def strtTime = null
	def stopTime = null
	def now = new Date()
	def sun = getSunriseAndSunset() // current based on geofence, previously was: def sun = getSunriseAndSunset(zipCode: zipCode)
	if(settings?.qStartTime && settings?.qStopTime) {
		if(settings?.qStartInput == "sunset") { strtTime = sun?.sunset }
		else if(settings?.qStartInput == "sunrise") { strtTime = sun?.sunrise }
		else if(settings?.qStartInput == "A specific time" && settings?.qStartTime) { strtTime = settings?.qStartTime }

		if(settings?.qStopInput == "sunset") { stopTime = sun?.sunset }
		else if(settings?.qStopInput == "sunrise") { stopTime = sun?.sunrise }
		else if(settings?.qStopInput == "A specific time" && settings?.qStopTime) { stopTime = settings?.qStopTime }
	} else { return true }
	if(strtTime && stopTime) {
		return timeOfDayIsBetween(strtTime, stopTime, new Date(), location.timeZone) ? false : true
	} else { return true }
}

Boolean quietDaysOk(days) {
	if(days) {
		def dayFmt = new SimpleDateFormat("EEEE")
		if(location.timeZone) { dayFmt.setTimeZone(location.timeZone) }
		return days?.contains(dayFmt.format(new Date())) ? false : true
	}
	return true
}

// Sends the notifications based on app settings
public sendMsg(String msgTitle, String msg, Boolean showEvt=true, Map pushoverMap=null, sms=null, push=null) {
	logger("trace", "sendMsg() | msgTitle: ${msgTitle}, msg: ${msg}, showEvt: ${showEvt}")
	String sentstr = "Push"
	Boolean sent = false
	try {
		String newMsg = "${msgTitle}: ${msg}"
		String flatMsg = newMsg.toString().replaceAll("\n", " ")
		if(!getOk2Notify()) {
			log.info "sendMsg: Message Skipped During Quiet Time ($flatMsg)"
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
				msgObj = pushoverMap ?: [title: msgTitle, message: msg, priority: (settings?.pushoverPriority?:0)]
				if(settings?.pushoverSound) { msgObj?.sound = settings?.pushoverSound }
				buildPushMessage(settings?.pushoverDevices, msgObj, true)
				sent = true
			}
			String smsPhones = sms ? sms.toString() : (settings?.smsNumbers?.toString() ?: null)
			if(smsPhones) {
				List phones = smsPhones?.toString()?.split("\\,")
				for (phone in phones) {
					String t0 = newMsg.take(140)
					if(showEvt) {
						sendSms(phone?.trim(), t0)	// send SMS and notification feed
					} else {
						sendSmsMessage(phone?.trim(), t0)	// send SMS
					}

				}
				sentstr = "Text Message to Phone [${phones}]"
				sent = true
			}
			if(sent) {
				state?.lastMsg = flatMsg
				state?.lastMsgDt = getDtNow()
				logger("debug", "sendMsg: Sent ${sentstr} (${flatMsg})")
			}
		}
	} catch (ex) {
		log.error "sendMsg $sentstr Exception:", ex
	}
	return sent
}

//PushOver-Manager Input Generation Functions
private getPushoverSounds(){return (Map) state?.pushoverManager?.sounds?:[:]}
private getPushoverDevices(){List opts=[];Map pmd=state?.pushoverManager?:[:];pmd?.apps?.each{k,v->if(v&&v?.devices&&v?.appId){Map dm=[:];v?.devices?.sort{}?.each{i->dm["${i}_${v?.appId}"]=i};addInputGrp(opts,v?.appName,dm);}};return opts;}
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
public pushover_handler(evt){Map pmd=state?.pushoverManager?:[:];switch(evt?.value){case"refresh":def ed = evt?.jsonData;String id = ed?.appId;Map pA = pmd?.apps?.size() ? pmd?.apps : [:];if(id){pA[id]=pA?."${id}"instanceof Map?pA[id]:[:];pA[id]?.devices=ed?.devices?:[];pA[id]?.appName=ed?.appName;pA[id]?.appId=id;pmd?.apps = pA;};pmd?.sounds=ed?.sounds;break;case "reset":pmd=[:];break;};state?.pushoverManager=pmd;}
//Builds Map Message object to send to Pushover Manager
private buildPushMessage(List devices,Map msgData,timeStamp=false){if(!devices||!msgData){return};Map data=[:];data?.appId=app?.getId();data.devices=devices;data?.msgData=msgData;if(timeStamp){data?.msgData?.timeStamp=new Date().getTime()};pushover_msg(devices,data);}


/******************************************
|    APP/DEVICE Version Functions
*******************************************/
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
	// logger("trace", "isCodeUpdateAvailable | type: $type | newVer: $newVer | curVer: $curVer | newestVersion: ${latestVer} | result: $result")
	return result
}

Boolean isAppUpdateAvail() {
	if(state?.versionData?.versions && state?.codeVersions?.mainApp && isCodeUpdateAvailable(state?.versionData?.versions?.mainApp?.ver, state?.codeVersions?.mainApp, "manager")) { return true }
	return false
}

Boolean isMonitorDevUpdateAvail() {
	if(state?.versionData?.versions && state?.codeVersions?.monitorDevice && isCodeUpdateAvailable(state?.versionData?.versions?.monitorDevice?.ver, state?.codeVersions?.monitorDevice, "dev")) { return true }
	return false
}

Boolean isEnergyDevUpdateAvail() {
	if(state?.versionData?.versions && state?.codeVersions?.energyDevice && isCodeUpdateAvailable(state?.versionData?.versions?.energyDevice?.ver, state?.codeVersions?.energyDevice, "dev")) { return true }
	return false
}

Boolean isServerUpdateAvail() {
	if(state?.versionData?.versions && state?.codeVersions?.server && isCodeUpdateAvailable(state?.versionData?.versions?.server?.ver, state?.codeVersions?.server, "server")) { return true }
	return false
}

Integer versionStr2Int(str) { return str ? str.toString()?.replaceAll("\\.", "")?.toInteger() : null }

def versionCheck(){
	state.versionWarning = ""
	state.thisDeviceVersion = ""

	def childExists = false
	def childDevs = getChildDevices(true)

	if (childDevs.size() > 0){
		childExists = true
		state.thisDeviceVersion = childDevs[0].devVersion()
		logger("debug", "child version found: " + state?.thisDeviceVersion)
	}

	logger("debug", "RM Device Handler Version: " + state?.thisDeviceVersion)

	if (state.thisSmartAppVersion != state.latestSmartAppVersion) {
		state.versionWarning = state.versionWarning + "Your SmartApp version (" + state.thisSmartAppVersion + ") is not the latest version (" + state.latestSmartAppVersion + ")\n\n"
	}
	if (childExists && state.thisDeviceVersion != state.latestDeviceVersion) {
		state.versionWarning = state.versionWarning + "Your RainMachine device version (" + state.thisDeviceVersion + ") is not the latest version (" + state.latestDeviceVersion + ")\n\n"
	}

	log.warn "${state.versionWarning}"
}

private getVersionInfo(oldVersion, newVersion){
	def params = [
		uri:  'http://www.fantasyaftermath.com/getVersion/sense/' +  oldVersion + '/' + newVersion,
		contentType: 'application/json'
	]
	asynchttp_v1.get('responseHandlerMethod', params)
}

private responseHandlerMethod(response, data) {
	if (response.hasError()) {
		log.error "response has error: $response.errorMessage"
	} else {
		def results = response.json
		state.latestSmartAppVersion = results.SmartApp;
		state.latestDeviceVersion = results.DoorDevice;
	}

	logger("debug", "previousVersion: " + state?.previousVersion)
	logger("debug", "installedVersion: " + state?.thisSmartAppVersion)
	logger("debug", "latestVersion: " + state?.latestSmartAppVersion)
	logger("debug", "deviceVersion: " + state?.latestDeviceVersion)
}

private checkVersionData(now = false) { //This reads a JSON file from GitHub with version numbers
	if (now || !state?.versionData || (getLastVerUpdSec() > (3600*6))) {
		if(now && (getLastVerUpdSec() < 300)) { return }
		getVersionData()
	}
}

private getVersionData() {
	def params = [
		uri:  "https://raw.githubusercontent.com/tonesto7/SmartThings_SenseMonitor/master/resources/versions.json",
		contentType: 'application/json'
	]
	try {
		httpGet(params) { resp->
			if(resp?.data) {
				log.info "Getting Latest Version Data from versions.json File"
				state?.versionData = resp?.data
				state?.lastVerUpdDt = getDtNow()
			}
		}
	} catch(ex) {
		log.error "getVersionData Exception: ", ex
	}
}

/******************************************
|    Time and Date Conversion Functions
*******************************************/
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

/******************************************
|   App Input Description Functions
*******************************************/
String getAppNotifConfDesc() {
	String str = ""
	if(pushStatus()) {
		def ap = getAppNotifDesc()
		def nd = getNotifSchedDesc()
		str += (settings?.usePush) ? "${str != "" ? "\n" : ""}Sending via: (Push)" : ""
		str += (settings?.pushoverEnabled) ? "${str != "" ? "\n" : ""}Pushover: (Enabled)" : ""
		str += (settings?.pushoverEnabled && settings?.pushoverPriority) ? "${str != "" ? "\n" : ""} • Priority: (${settings?.pushoverPriority})" : ""
		str += (settings?.pushoverEnabled && settings?.pushoverSound) ? "${str != "" ? "\n" : ""} • Sound: (${settings?.pushoverSound})" : ""
		str += (settings?.phone) ? "${str != "" ? "\n" : ""}Sending via: (SMS)" : ""
		str += (ap) ? "${str != "" ? "\n\n" : ""}Enabled Alerts:\n${ap}" : ""
		str += (ap && nd) ? "${str != "" ? "\n" : ""}\nAlert Restrictions:\n${nd}" : ""
	}
	return str != "" ? str : null
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

String getServiceConfDesc() {
	String str = ""
	str += (settings?.stHub) ? "${str != "" ? "\n" : ""}Hub Info:" : ""
	str += (settings?.stHub) ? "${str != "" ? "\n" : ""} • IP: ${settings?.stHub?.getLocalIP()}" : ""
	str += (settings?.websocketPollingInterval || settings?.refreshInterval) ? "\n\nServer Push Settings:" : ""
	str += (settings?.websocketPollingInterval) ? "${str != "" ? "\n" : ""} • Websocket Poll Interval: (${settings?.websocketPollingInterval}sec)" : ""
	str += (settings?.refreshInterval) ? "${str != "" ? "\n" : ""} • Monitor Refresh Interval: (${settings?.refreshInterval}sec)" : ""
	return str != "" ? str : null
}

String getAppNotifDesc() {
	def str = ""
	str += settings?.sendMissedPollMsg != false ? "${str != "" ? "\n" : ""} • Missed Poll Alerts" : ""
	str += settings?.sendAppUpdateMsg != false ? "${str != "" ? "\n" : ""} • Code Updates" : ""
	return str != "" ? str : null
}

String getServInfoDesc() {
	Map rData = state?.nodeServiceInfo
	String str = ""
	String dtstr = ""
	if(rData?.startupDt) {
		def dt = rData?.startupDt
		dtstr += dt?.y ? "${dt?.y}yr${dt?.y > 1 ? "s" : ""}, " : ""
		dtstr += dt?.mn ? "${dt?.mn}mon${dt?.mn > 1 ? "s" : ""}, " : ""
		dtstr += dt?.d ? "${dt?.d}day${dt?.d > 1 ? "s" : ""}, " : ""
		dtstr += dt?.h ? "${dt?.h}hr${dt?.h > 1 ? "s" : ""} " : ""
		dtstr += dt?.m ? "${dt?.m}min${dt?.m > 1 ? "s" : ""} " : ""
		dtstr += dt?.s ? "${dt?.s}sec" : ""
	}
	str += " ├ IP: (${rData?.ip})"
	str += "\n ├ Port: (${rData?.port})"
	str += "\n ├ Version: (v${rData?.version})"
	str += "\n ${dtstr != "" ? "├" : "└"} Session Events: (${rData?.sessionEvts})"
	str += dtstr != "" ? "\n └ Uptime: ${dtstr.length() > 20 ? "\n     └ ${dtstr}" : "${dtstr}"}" : ""
	return str != "" ? str : null
}

String getInputToStringDesc(inpt, addSpace = null) {
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

private logger(type, msg) {
	if(type && msg && settings?.appDebug) {
		log."${type}" "${msg}"
	}
}
