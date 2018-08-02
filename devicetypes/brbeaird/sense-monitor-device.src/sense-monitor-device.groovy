/**
 *	Sense Device
 *
 *	Author: Brian Beaird
 *  Last Updated: 2018-07-29
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
       input "prefNotifyOff", "bool", required: false, title: "Push notifications when turned off?"
    }

	simulator { }
    
    

	tiles {
        
        standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
            state "off", label: '${currentValue}', action: "switch.on",
                  icon: "st.switches.switch.off", backgroundColor: "#ffffff"
            state "on", label: '${currentValue}', action: "switch.off",
                  icon: "st.switches.switch.on", backgroundColor: "#00a0dc"
        }

        // value tile (read only)
        valueTile("power", "device.power", decoration: "flat", width: 2, height: 2) {
            state "power", label:'${currentValue} Watts'
        }
        
        valueTile("lastUpdated", "device.lastUpdated", height: 1, width: 3, inactiveLabel: false, decoration: "flat") {
			state("lastUpdatedValue", label:'Last updated: ${currentValue}', backgroundColor:"#ffffff")
		}
        
        
        
        main(["switch", "power"])
        details(["switch", "power", "lastUpdated"])
    }
}

/*for testing only
def on(){
	log.debug "Updating status to on"
    sendEvent(name: "switch", value: "on", display: true, displayed: true, isStateChange: true, descriptionText: device.displayName + " was off")
}


def off(){
    log.debug "Updating status to off"
    sendEvent(name: "switch", value: "off", display: true, displayed: true, isStateChange: true, descriptionText: device.displayName + " was off")    
}
*/
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


def updateDeviceStatus(senseDevice){
	def devName = device.displayName
    devName = devName.replace("Sense-", "")
    log.debug "name " + devName
    def oldStatus = device.currentValue("switch")
    log.debug "Old status was " + oldStatus
    log.debug "New status is: " + senseDevice.state
    if (senseDevice.state == "off"){
    	if (oldStatus != "off"){
            log.debug "Updating status to off"
            sendEvent(name: "switch", value: "off", display: true, displayed: true, isStateChange: true, descriptionText: device.displayName + " was off")
            if (prefNotifyOff && !(parent.quietModes.contains(location.currentMode))){                                
                parent.sendPushMessage(devName + " turned off!")
            }
        }
    }
    if (senseDevice.state == "on"){
    	if (oldStatus != "on"){
            log.debug "Updating status to on"
            sendEvent(name: "switch", value: "on", display: true, displayed: true, isStateChange: true, descriptionText: device.displayName + " was on")
            if (prefNotifyOn && !(parent.quietModes.contains(location.currentMode))){                                
            	parent.sendPushMessage(devName + " turned on!")
            }
        }
    }
    
    if (device.currentValue("power") != senseDevice.usage){
        log.debug "Updating usage to " + senseDevice.usage
        sendEvent(name: "power", value: senseDevice.usage, display: true, displayed: true, isStateChange: true)        
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

