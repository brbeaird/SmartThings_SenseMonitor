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
    str += "\nVersion: ${appVersion()}"
    section() { paragraph str, image: getAppImg("sense.2x.png") }
}

def mainPage() {
    checkVersionData(true)
    // state.previousVersion = state.thisSmartAppVersion
    // if (state.previousVersion == null){
    //     state.previousVersion = 0;
    // }
    // state.thisSmartAppVersion = "0.1.0"
    // getVersionInfo(0, 0)
	dynamicPage(name: "mainPage", uninstall: false, install: true) {
        appInfoSect()
        section("Sense Devices:") {
            List devs = getDeviceList()?.collect { "${it?.value?.name} (${it?.value?.usage ?: 0} W)" }?.sort()
            paragraph title: "Discovered Devices:", "${devs?.size() ? devs?.join("\n") : "No Devices Available"}"
            input "senseDeviceFilter", "enum", title: "Only Create/Update these Devices", description: "Tap to select", options: (getDeviceList(true)?:[]), multiple: true, required: false, submitOnChange: true, image: getAppImg("power.png")
        }
        section("Notifications:") {
            def t0 = getAppNotifConfDesc()
            href "notifPrefPage", title: "App and Device\nNotifications", description: (t0 ? "${t0}\n\nTap to modify" : "Tap to configure"), state: (t0 ? "complete" : null), image: getAppImg("notification_icon2.png")
        }
        section("Logging:") {
            def dbgDesc = getAppDebugDesc()
            href "debugPrefPage", title: "Logging", description: (dbgDesc ? "${dbgDesc ?: ""}\n\nTap to modify..." : "Tap to configure..."), state: ((isAppDebug() || isChildDebug()) ? "complete" : null), image: getAppImg("log.png")
        }
        section("") {
            href "uninstallPage", title: "Uninstall this App", description: "Tap to Remove...", image: getAppImg("uninstall_icon.png")
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

def debugPrefPage() {
	dynamicPage(name: "debugPrefPage", install: false) {
		section ("Application Logs") {
			input (name: "appDebug", type: "bool", title: "Show App Logs in the IDE?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("log.png"))
		}
		section ("Child Device Logs") {
			input (name: "childDebug", type: "bool", title: "Show Device Logs in the IDE?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("log.png"))
		}
	}
}

def notifPrefPage() {
	dynamicPage(name: "notifPrefPage", install: false) {
        Integer pollWait = 900
        Integer pollMsgWait = 3600
        Integer updNotifyWait = 7200

		section("Enable Push Messages:") {
			input "usePush", "bool", title: "Send Push Notitifications\n(Optional)", required: false, submitOnChange: true, defaultValue: false, image: getAppImg("notification_icon.png")
		}
		section("Enable Text Messaging:") {
			input "phones", "phone", title: "Send SMS to Number\n(Optional)", required: false, submitOnChange: true, image: getAppImg("notification_icon2.png")
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
							input "pushoverPriority", "enum", title: "Notification Priority (Optional)", description: "Tap to select", defaultValue: "Normal", required: false, multiple: false, submitOnChange: true, options: t0
							input "pushoverSound", "enum", title: "Notification Sound (Optional)", description: "Tap to select", defaultValue: "pushover", required: false, multiple: false, submitOnChange: true, options: getPushoverSounds()
						}
					}
				} else { paragraph "New Install Detected!!!\n\n1. Press Done to Finish the Install.\n2. Goto the Automations Tab at the Bottom\n3. Tap on the SmartApps Tab above\n4. Select ${app?.getLabel()} and Resume configuration", state: "complete" }
			}
		}
		if(settings?.phone || settings?.usePush || (settings?.pushoverEnabled && settings?.pushoverDevices)) {
			if((settings?.usePush || (settings?.pushoverEnabled && settings?.pushoverDevices)) && !state?.pushTested && state?.pushoverManager) {
				if(sendMsg("Info", "Push Notification Test Successful. Notifications Enabled for ${app?.label}", true)) {
					state.pushTested = true
				}
			}
			section("Notification Restrictions:") {
				def t1 = getNotifSchedDesc()
				href "setNotificationTimePage", title: "Notification Restrictions", description: (t1 ?: "Tap to configure"), state: (t1 ? "complete" : null), image: getAppImg("restriction_icon.png")
			}
			section("Missed Poll Alerts:") {
				input (name: "sendMissedPollMsg", type: "bool", title: "Send Missed Checkin Alerts?", defaultValue: true, submitOnChange: true, image: getAppImg("late_icon.png"))
				if(settings?.sendMissedPollMsg) {
					def misPollNotifyWaitValDesc = settings?.misPollNotifyWaitVal ?: "Default: 15 Minutes"
					input (name: "misPollNotifyWaitVal", type: "enum", title: "Time Past the Missed Checkin?", required: false, defaultValue: 900, metadata: [values:notifValEnum()], submitOnChange: true)
					if(settings?.misPollNotifyWaitVal) {
						if (settings?.misPollNotifyWaitVal.toInteger() == 1000000) {
							input (name: "misPollNotifyWaitValCust", type: "number", title: "Custom Missed Checkin Value in Seconds", range: "60..86400", required: false, defaultValue: 900, submitOnChange: true)
							if(settings?.misPollNotifyWaitValCust) { pollWait = settings?.misPollNotifyWaitValCust }
						}
					}

					def misPollNotifyMsgWaitValDesc = settings?.misPollNotifyMsgWaitVal ?: "Default: 1 Hour"
					input (name: "misPollNotifyMsgWaitVal", type: "enum", title: "Delay before sending again?", required: false, defaultValue: 3600, metadata: [values:notifValEnum()], submitOnChange: true)
					if(settings?.misPollNotifyMsgWaitVal) {
						if (settings?.misPollNotifyMsgWaitVal.toInteger() == 1000000) {
							input (name: "misPollNotifyMsgWaitValCust", type: "number", title: "Custom Msg Wait Value in Seconds", range: "60..86400", required: false, defaultValue: 3600, submitOnChange: true)
							if(settings?.misPollNotifyMsgWaitValCust) { pollMsgWait = settings?.misPollNotifyMsgWaitValCust }
						}
					}
				}
			}
			section("Code Update Alerts:") {
				input (name: "sendAppUpdateMsg", type: "bool", title: "Send for Updates...", defaultValue: true, submitOnChange: true, image: getAppImg("update_icon.png"))
				if(settings?.sendAppUpdateMsg) {
					def updNotifyWaitValDesc = settings?.updNotifyWaitVal ?: "Default: 2 Hours"
					input (name: "updNotifyWaitVal", type: "enum", title: "Send reminders every?", required: false, defaultValue: 7200, metadata: [values:notifValEnum()], submitOnChange: true)
					if(settings?.updNotifyWaitVal) {
						if (settings?.updNotifyWaitVal.toInteger() == 1000000) {
							input (name: "updNotifyWaitValCust", type: "number", title: "Custom Missed Poll Value in Seconds", range: "30..86400", required: false, defaultValue: 7200, submitOnChange: true)
							if(settings?.updNotifyWaitValCust) { updNotifyWait = settings?.updNotifyWaitValCust }
						}
					}
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

def installed() {
    log.debug "Installed with settings: ${settings}"
    state?.isInstalled = true
    initialize()
}

def updated() {
    if (state?.previousVersion != state?.thisSmartAppVersion){
        getVersionInfo(state?.previousVersion, state?.thisSmartAppVersion);
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
    subscribe(app, onAppTouch)
    subscribe(location, null, lanEventHandler, [filterEvents:false])
    stateCleanup()
    updCodeVerMap()
}

private stateCleanup() {
    List items = ["availableDevices"]
    items?.each { si-> if(state?.containsKey(si as String)) { state?.remove(si)} }
}

def onAppTouch(evt) {
    log.trace "appTouch..."
	notificationCheck()
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

def lanEventHandler(evt) {
    def description = evt.description
    def hub = evt?.hubId
    def msg = parseLanMessage(evt.description)
    //def parsedEvent = parseLanMessage(description)
    def body = msg.body
    def headerMap = msg.headers      // => headers as a Map

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

        if (result?.devices){
            //log.debug result.versionInfo.SmartApp
            Map senseDeviceMap = [:]
            result?.devices?.each { senseDevice ->
                senseDeviceMap[senseDevice?.id] = senseDevice
                // log.debug "senseDevice(${senseDevice.name}): ${senseDevice}"
                log.debug "${senseDevice.name} | State: (${senseDevice?.state?.toString().toUpperCase()}) | Usage: ${senseDevice?.usage}W"
                
                //def senseDevice = result.devices[0]
                def dni = [ app?.id, (senseDevice != "SenseMonitor" ? "senseDevice" : "senseMonitor"), senseDevice?.id].join('|')
                //log.debug "device DNI will be: " + dni + " for " + senseDevice.name
                def childDevice = getChildDevice(dni)
                def childDeviceAttrib = [:]
                def fullName = senseDevice?.id != "SenseMonitor" ? "Sense-" + senseDevice?.name : "Sense-Monitor"
                if (!childDevice){
                    log.debug "name will be: " + fullName
                    //childDeviceAttrib = ["name": senseDevice.name, "completedSetup": true]
                    childDeviceAttrib = ["name": fullName, "completedSetup": true]

                    try{ 
                        if(senseDevice?.id == "SenseMonitor") {
                            log.debug "Creating NEW Sense Monitor Device: " + fullName
                        } else { log.debug "Creating NEW Sense Device: " + fullName }
                        childDevice = addChildDevice("brbeaird", "Sense Monitor Device", dni, null, childDeviceAttrib)
                        childDevice?.updateDeviceStatus(senseDevice)
                    } catch(physicalgraph.app.exception.UnknownDeviceTypeException e) {
                        log.error "AddDevice Error! ", e
                        //state.installMsg = state.installMsg + deviceName + ": problem creating RM device. Check your IDE to make sure the brbeaird : RainMachine device handler is installed and published. \r\n\r\n"
                    }
                } else {
                    //Check and see if name needs a refresh
                    if (childDevice?.name != fullName || childDevice?.label != fullName){
                        log.debug ("Updating device name (old label was " + childDevice?.label + " old name was " + childDevice?.name + " new hotness: " + fullName)
                        childDevice?.name = fullName
                        childDevice?.label = fullName
                        //state.installMsg = state.installMsg + deviceName + ": updating device name (old name was " + childDevice.label + ") \r\n\r\n"
                    }
                    modCodeVerMap("senseDevice", childDevice?.devVersion()) // Used for the Updater Notifiers
                    
                    childDevice?.updateDeviceStatus(senseDevice)
                }
                state?.lastDevDataUpd = getDtNow()
            }
            state?.senseDeviceMap = senseDeviceMap
        }
    }
}

/******************************************
|    Notification Functions
*******************************************/
Map notifValEnum(allowCust = true) {
	Map items = [
		60:"1 Minute", 300:"5 Minutes", 600:"10 Minutes", 900:"15 Minutes", 1200:"20 Minutes", 1500:"25 Minutes",
		1800:"30 Minutes", 3600:"1 Hour", 7200:"2 Hours", 14400:"4 Hours", 21600:"6 Hours", 43200:"12 Hours", 86400:"24 Hours"
	]
    if(allowCust) { items[100000] = "Custom" }
	return items 
}

private notificationCheck() {
    // log.trace "notificationCheck"
    checkVersionData()
	if(!getOk2Notify()) { return }
	missPollNotify((settings?.sendMissedPollMsg == true), (state?.misPollNotifyMsgWaitVal ?: 3600))
	appUpdateNotify()
}

private missPollNotify(Boolean on, Integer wait) {
    // log.trace "missPollNotify | on: $on | wait: $wait) | getLastDevicePollSec: ${getLastDevicePollSec()} | misPollNotifyWaitVal: ${state?.misPollNotifyWaitVal} | getLastMisPollMsgSec: ${getLastMisPollMsgSec()}"
	if(!on || !wait || !(getLastDevicePollSec() > (state?.misPollNotifyWaitVal ?: 900))) { return }
	if(!(getLastMisPollMsgSec() > wait.toInteger())) { 
        return 
    } else {
		def msg = "\nThe app has not refreshed energy data in the last (${getLastDevicePollSec()}) seconds.\nPlease try refreshing data using device refresh button."
		log.warn msg.toString().replaceAll("\n", " ")
		if(sendMsg("${app.name} Data Refresh Issue", msg)) {
			state?.lastMisPollMsgDt = getDtNow()
		}
	}
}

private appUpdateNotify() {
	Boolean on = (settings?.sendAppUpdateMsg != false)
	Boolean appUpd = isAppUpdateAvail()
	Boolean devUpd = isDevUpdateAvail()
	if(getLastUpdMsgSec() > state?.updNotifyWaitVal.toInteger()) {
		if(appUpd || devUpd) {
			def str = ""
			str += !appUpd ? "" : "${str == "" ? "" : "\n"}Sense App: v${state?.versionData?.versions?.mainApp?.ver?.toString()}"
			str += !devUpd ? "" : "${str == "" ? "" : "\n"}Sense Device: v${state?.versionData?.versions?.senseDevice?.ver?.toString()}"
			sendMsg("Info", "Sense Monitor Update(s) are Available:${str}...\n\nPlease visit the IDE to Update your code...")
			state?.lastUpdMsgDt = getDtNow()
		}
	}
}

public sendMsg(String msgType, String msg, Boolean showEvt=true, Map pushoverMap=null, sms=null, push=null) {
	//log.trace("sendMsg:  msgType: ${msgType}, msg: ${msg}, showEvt: ${showEvt}")
	logger("trace", "sendMsg")
	def sentstr = "Push"
	def sent = false
	try {
		def newMsg = "${msgType}: ${msg}" as String
		def flatMsg = newMsg.toString().replaceAll("\n", " ")
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


private checkVersionData(now = false) { //This reads a JSON file from GitHub with version numbers
	if (now && !state?.versionData || (getLastVerUpdSec() > (3600*6))) {
        if(canSchedule()) { 
            getVersionData(now)
        } else {
            runIn(45, "getVersionData", [overwrite: true])
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
	Boolean res = true
	if(modeEntry) {
		modeEntry?.each { m -> if(m.toString() == location?.mode.toString()) { res = false } }
	}
	return res
}

Boolean notificationTimeOk() {
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

Boolean daysOk(days) {
	if(days) {
		def dayFmt = new SimpleDateFormat("EEEE")
		if(location.timeZone) { dayFmt.setTimeZone(location.timeZone) }
		return days?.contains(dayFmt.format(new Date())) ? false : true
	} else { return true }
}


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
	// log.trace ("type: $type | newVer: $newVer | curVer: $curVer | newestVersion: ${latestVer} | result: $result")
	return result
}

Boolean isAppUpdateAvail() {
	if(isCodeUpdateAvailable(state?.versionData?.versions?.mainApp?.ver, state?.codeVersions?.mainApp, "manager")) { return true }
	return false
}

Boolean isDevUpdateAvail() {
	if(isCodeUpdateAvailable(state?.versionData?.versions?.senseDevice?.ver, state?.codeVersions?.senseDevice, "dev")) { return true }
	return false
}

def versionCheck(){
    state.versionWarning = ""
    state.thisDeviceVersion = ""

    def childExists = false
    def childDevs = getChildDevices()

    if (childDevs.size() > 0){
        childExists = true
        state.thisDeviceVersion = childDevs[0].showVersion()
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

private getVersionData(now=false) {
    def params = [
        uri:  "https://raw.githubusercontent.com/tonesto7/SmartThings_SenseMonitor/master/resources/versions.json",
        contentType: 'application/json'
    ]
    if(now) {
        httpGet(params) { resp->
            versionDataRespHandler(resp, null)
        }
    } else {
        asynchttp_v1.get('versionDataRespHandler', params)
    }
}

private versionDataRespHandler(resp, data) {
    try {
        if(resp.data) {
            log.info "Getting Latest Version Data from versions.json File..."
            state?.versionData = resp?.data
            state?.lastVerUpdDt = getDtNow()
        }
        logger("trace", "versionDataRespHandler Resp: ${resp?.data}")
    } catch(ex) {
        log.error "versionDataRespHandler Error: ", ex
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
		str += (ap != null) ? "${str != "" ? "\n" : ""}\nEnabled Alerts:\n${ap}" : ""
		str += (nd != null) ? "${str != "" ? "\n" : ""}\nAlert Restrictions:\n${nd}" : ""
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

String getAppNotifDesc() {
	def str = ""
	str += settings?.sendMissedPollMsg != false ? "${str != "" ? "\n" : ""} • Missed Poll Alerts: (${strCapitalize(settings?.sendMissedPollMsg ?: "True")})" : ""
	str += settings?.sendAppUpdateMsg != false ? "${str != "" ? "\n" : ""} • Code Updates: (${strCapitalize(settings?.sendAppUpdateMsg ?: "True")})" : ""
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
