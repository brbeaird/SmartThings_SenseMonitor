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

 include 'asynchttp_v1'

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
    page(name: "prefConfigure", title: "Sense")
    //TODO: Add version Checking
    //TODO: Add preference to exclude Sense devices
    //TODO: Add preference to NOT auto re-sync names
}

String appVersion() { return "0.2.0" }
String appAuthor() { return "Brian Beaird" }
String gitBranch() { return "tonesto7" }
String getAppImg(imgName) 	{ return "https://raw.githubusercontent.com/${gitBranch()}/SmartThings_SenseMonitor/master/resources/icons/$imgName" }

def appInfoSect(sect=true)	{
	def str = ""
    str += "${app?.name}"
    str += "\nAuthor: ${appAuthor()}"
    str += "\nVersion: ${state.thisSmartAppVersion}"
    section() { paragraph str, image: getAppImg("sense.2x.png") }
}

def prefConfigure(){
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
        if (headerMap.source != "STSense"){
            //log.debug "Non-sense data detected - ignoring."
            return 0
        }
    }

    Map result = [:]
    if (body != null){
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
                log.debug "senseDevice(${senseDevice.name}): ${senseDevice}"  //Yay
                
                //def senseDevice = result.devices[0]
                def dni = [ app.id, (senseDevice != "SenseMonitor" ? "senseDevice" : ""), senseDevice.id].join('|')
                //log.debug "device DNI will be: " + dni + " for " + senseDevice.name
                def childDevice = getChildDevice(dni)
                def childDeviceAttrib = [:]
                def fullName = senseDevice?.id != "SenseMonitor" ? "Sense-" + senseDevice?.name : senseDevice.name
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
                }
                else{
                    //Check and see if name needs a refresh
                    if (childDevice.name != fullName || childDevice.label != fullName){
                        log.debug ("Updating device name (old label was " + childDevice.label + " old name was " + childDevice.name + " new hotness: " + fullName)
                        childDevice.name = fullName
                        childDevice.label = fullName
                        //state.installMsg = state.installMsg + deviceName + ": updating device name (old name was " + childDevice.label + ") \r\n\r\n"
                    }
                    //Update if something has recently changed
                    //if (senseDevice.recentlyChanged){
                        //log.debug "Updating " + fullName
                    childDevice.updateDeviceStatus(senseDevice)
                    //}
                }
            }
            atomicState?.senseDeviceMap = senseDeviceMap
        }
    }
}

def getVersionInfo(oldVersion, newVersion){
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
    asynchttp_v1.get('responseHandlerMethod', params)
}

private versionDataRespHandler(resp, data) {
    if(resp.hasError()) {

    } else {
        if(resp.data) {
            log.info "Getting Latest Version Data from versions.json File..."
            state?.versionData = resp?.data
            state?.lastVerUpdDt = getDtNow()
            updateHandler()
        }
        log.trace ("getWebFileData Resp: ${resp?.data}")
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

def updateHandler() {
	//log.trace "updateHandler..."
	if(state?.isInstalled) {
		if(state?.versionData?.updater?. && atomicState?.lastCritUpdateInfo?.ver.toInteger() != atomicState?.appData?.updater?.updateVer.toInteger()) {
			if(sendMsgNew("Critical", "There are Critical Updates available for the Efergy Manager Application!!! Please visit the IDE and make sure to update the App and Device Code...")) {
				atomicState?.lastCritUpdateInfo = ["dt":getDtNow(), "ver":atomicState?.appData?.updater?.updateVer?.toInteger()]
			}
		}
		if(atomicState?.appData?.updater?.updateMsg != "" && atomicState?.appData?.updater?.updateMsg != atomicState?.lastUpdateMsg) {
			if(getLastUpdateMsgSec() > 86400) {
				if(sendMsgNew("Info", "${atomicState?.updater?.updateMsg}")) {
					atomicState?.lastUpdateMsgDt = getDtNow()
				}
			}
		}
	}
}

def isCodeUpdateAvailable(String newVer, String curVer, String type) {
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

def isAppUpdateAvail() {
	if(isCodeUpdateAvailable(atomicState?.appData?.updater?.versions?.app?.ver, appVer(), "manager")) { return true }
	return false
}

def isDevUpdateAvail() {
	if(isCodeUpdateAvailable(atomicState?.appData?.updater?.versions?.dev?.ver, atomicState?.devVer, "dev")) { return true }
	return false
}

def formatDt(dt, tzChg=true) {
	def tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	if(tzChg) { if(getTimeZone()){ tf.setTimeZone(getTimeZone()) } }
	return tf?.format(dt)
}

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