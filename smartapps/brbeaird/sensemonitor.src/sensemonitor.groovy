/**
 *  SenseMonitor
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
definition(
    name: "SenseMonitor",
    namespace: "brbeaird",
    author: "Brian Beaird",
    description: "Connects SmartThings with Sense",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Title") {
    	input "quietModes", "mode", title: "Do not send push notifications during these modes", multiple: true		
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
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
    //log.debug evt.description
    //if (parsedEvent != null){
        //log.debug parsedEvent.data
        //log.debug parsedEvent.data.devices
       //}
    
    //Filter out calls from other LAN devices
    if (headerMap != null){
    	if (headerMap.source != "STSense"){
        	//log.debug "Non-sense data detected - ignoring."
        	return 0
        }
        
        //log.debug "no headers found"
    	//return 0
    }
    
    
    def result
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
        Put dni and name of each into state's "availableDevices" map
        If dni is in "prefIgnoreDev" list, do not create child
        
        */
        
        
        if (result.devices){
        	result.devices.each { senseDevice ->
            
                //log.debug result.devices[0]  //Yay
                //def senseDevice = result.devices[0]
                def dni = [ app.id, "senseDevice", senseDevice.id].join('|')
                //log.debug "device DNI will be: " + dni + " for " + senseDevice.name
                def childDevice = getChildDevice(dni)
                def childDeviceAttrib = [:]
                def fullName = "Sense-" + senseDevice.name
                if (!childDevice){                    
                    log.debug "name will be: " + fullName
                    //childDeviceAttrib = ["name": senseDevice.name, "completedSetup": true]
                    childDeviceAttrib = ["name": fullName, "completedSetup": true]

                    try{
                        log.debug "Creating new Sense device: " + fullName
                        childDevice = addChildDevice("brbeaird", "Sense Monitor Device", dni, null, childDeviceAttrib)
                        childDevice.updateDeviceStatus(senseDevice)
                    }
                    catch(physicalgraph.app.exception.UnknownDeviceTypeException e)
                    {
                        log.debug "Error! " + e
                        //state.installMsg = state.installMsg + deviceName + ": problem creating RM device. Check your IDE to make sure the brbeaird : RainMachine device handler is installed and published. \r\n\r\n"
                    }
                }
                else{
                    //log.debug "Updating " + fullName
                    childDevice.updateDeviceStatus(senseDevice)
                    if (childDevice.name != fullName || childDevice.label != fullName){
                    	log.debug ("Updating device name (old label was " + childDevice.label + " old name was " + childDevice.name + " new hotness: " + fullName)
                        childDevice.name = fullName
                        childDevice.label = fullName
                        //state.installMsg = state.installMsg + deviceName + ": updating device name (old name was " + childDevice.label + ") \r\n\r\n"
                	}                
                }            
            }            
        }    	
    }	
}