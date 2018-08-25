/**
 *	Sense Monitor SmartApp
 *
 *	Author: Brian Beaird
 *  Last Updated: 2018-08-24 (By A. Santilli)
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
    iconUrl: "https://raw.githubusercontent.com/brbeaird/SmartThings_SenseMonitor/master/icons/sense.1x.png",
    iconX2Url: "https://raw.githubusercontent.com/brbeaird/SmartThings_SenseMonitor/master/icons/sense.2x.png",
    iconX3Url: "https://raw.githubusercontent.com/brbeaird/SmartThings_SenseMonitor/master/icons/sense.3x.png")


preferences {
    page(name: "prefConfigure", title: "Sense")
    //TODO: Add version Checking
    //TODO: Add preference to exclude Sense devices
    //TODO: Add preference to NOT auto re-sync names
}

def prefConfigure(){
    state.previousVersion = state.thisSmartAppVersion
    if (state.previousVersion == null){
        state.previousVersion = 0;
    }
    state.thisSmartAppVersion = "0.1.0"
    getVersionInfo(0, 0)


    return dynamicPage(name: "prefConfigure", title: "Configure Sense Devices", uninstall:true, install: true) {
        section("Filter Notifications") {
            input "quietModes", "mode", title: "Do not send push notifications during these modes", multiple: true
        }
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    if (state.previousVersion != state.thisSmartAppVersion){
        getVersionInfo(state.previousVersion, state.thisSmartAppVersion);
    }
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
            result.devices.each { senseDevice ->

                log.debug "senseDevice(${senseDevice.name}): ${senseDevice}"  //Yay
                
                //def senseDevice = result.devices[0]
                def dni = [ app.id, (senseDevice != "SenseMonitor" ? "senseDevice" : ""), senseDevice.id].join('|')
                //log.debug "device DNI will be: " + dni + " for " + senseDevice.name
                def childDevice = getChildDevice(dni)
                def childDeviceAttrib = [:]
                def fullName = senseDevice.id != "SenseMonitor" ? "Sense-" + senseDevice.name : senseDevice.name
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