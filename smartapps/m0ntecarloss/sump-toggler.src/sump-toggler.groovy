/**
 *  Sump Toggler
 *
 *  Copyright 2016 Chris Roberts
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
    name: "Sump Toggler",
    namespace: "m0ntecarloss",
    author: "Chris Roberts",
    description: "Toggles my 'sump' pump on for a while periodically",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Select sump switch") {
        input "sump_switch", "capability.switch", title: "Which?", required: true, multiple: true
	}
    section("How often should sump switch be turned on?") {
        input name: "cycletime", type: "enum", title: "Select", options: ["5 minutes", "10 minutes", "15 minutes", "30 minutes", "1 hour", "3 hours", "4 hours"], required: true
    }
    section("Minutes to run when toggled?") {
        input "runtime_minutes", "number", title: "Minutes?", required: true
    }
    section( "Outdoor temp sensor" ) {
		input "temp_sensors", "capability.temperatureMeasurement", title: "Which?", required: false, multiple: true
	}
    section("Cutoff Temp") {
        input "cutoff_temp", "number", title: "Degrees", required: false
    }
    section(hideable: True, "Debug") {
        input "debugEnabled", "bool", title: "Debug Enabled", required: True
    }
}

def installed() {
	DEBUG("Installed with settings: ${settings}")

	unsubscribe()
    unschedule()
	initialize()
}

def updated() {
	DEBUG("Updated with settings: ${settings}")

	unsubscribe()
    unschedule()
	initialize()
}

def initialize() {
	DEBUG("initialize")
    unschedule()
    
    DEBUG("initialize: settings.runtime_minutes = ${settings.runtime_minutes}")
    
    // calculate when we should turn off
    state.runInSeconds = settings.runtime_minutes * 60
    
    DEBUG("initialize: state.runInSeconds = ${state.runInSeconds}")
   
    if(settings.cutoff_temp == null) {
        settings.cutoff_temp = 35
        DEBUG("Cutoff temp was null, defaulting it to ${settings.cutoff_temp}")
    }
    
    DEBUG("Temp Sensors:     ${temp_sensors}")
    DEBUG("Temp cutoff temp: ${settings.cutoff_temp}")
    
    subscribe(sump_switch, "switch.on", switchOnHandler)
    switch (settings.cycletime)
    {
        case "5 minutes":
            DEBUG("initialize: runEvery5Minutes(turnOn)")
            runEvery5Minutes(turnOn)
            break
        case "10 minutes":
            DEBUG("initialize: runEvery10Minutes(turnOn)")
            runEvery10Minutes(turnOn)
            break
        case "15 minutes":
            DEBUG("initialize: runEvery15Minutes(turnOn)")
            runEvery15Minutes(turnOn)
            break
        case "30 minutes":
            DEBUG("initialize: runEvery30Minutes(turnOn)")
            runEvery30Minutes(turnOn)
            break
        case "1 hour":
            DEBUG("initialize: runEvery1Hour(turnOn)")
            runEvery1Hour(turnOn)
            break
        case "3 hours":
            DEBUG("initialize: runEvery3Hours(turnOn)")
            runEvery3Hours(turnOn)
            break
        case "4 hours":
            DEBUG("initialize: schedule(15 29 2,6,10,14,18,22 * * ?, turnOn)")
            schedule("15 29 2,6,10,14,18,22 * * ?", turnOn)
            break
        default:
            DEBUG("initialize:  *** INVALID VALUE FOR cycletime of ${settings.cycletime} ***")
            DEBUG("initialize:  *** USING 30 MINUTES                                     ***")
            runEvery5Minutes(turnOn)
            break
    }
}

def switchOnHandler(evt) {
    DEBUG("switchOnHandler: ${evt}")
    DEBUG("switchOnHandler:    scheduling turn off in ${state.runInSeconds} seconds")
    try {
        runIn(state.runInSeconds, turnOff)
    } catch (e) {
        log.error "turnOff: SCREWED trying to schedule runIn --> ${e}"
        sendNotificationEvent("turnOff: SCREWED trying to schedule runIn --> ${e}")
    }
    
}

def turnOff2() {
    DEBUG("turnOff2 called")
    for(xxx in sump_switch)
        DEBUG("turnOff2:    switch [${xxx.displayName}] is [${xxx.currentSwitch}]")
    sump_switch.off()
}

def turnOff() {
    DEBUG("turnOff called")
    for(xxx in sump_switch)
        DEBUG("turnOff:    switch [${xxx.displayName}] is [${xxx.currentSwitch}]")
    sump_switch.off()
    DEBUG("turnOff:    scheduling turn off in 90 seconds as a precaution")
    runIn(90, turnOff2)
}

def turnOn() {
    DEBUG("turnOn called")
    def OK = true
 
    unschedule("turnOff")
    unschedule("turnOff2")
    
    for(xxx in temp_sensors) {
        def curr_temp = xxx.latestValue("temperature")
        if(curr_temp != null) {
            DEBUG("Temp sensor [${xxx.device.displayName}] shows temp of [${curr_temp}]")
            if(curr_temp < settings.cutoff_temp) {
                DEBUG("Temp [${curr_temp}] < [${settings.cutoff_temp}] so no turn on and stuff")
                OK = false
            }
        }
    }
    
    DEBUG("OK to turn shit on = ${OK}")
   
    if( OK == true ) {
    
        for(xxx in sump_switch)
            DEBUG("turnOn:    switch [${xxx.displayName}] is [${xxx.currentSwitch}]")
        DEBUG("turnOn:    scheduling turn off in ${state.runInSeconds} seconds")
        try {
            runIn(state.runInSeconds, turnOff)
        } catch (e) {
            log.error "turnOn: SCREWED trying to schedule runIn --> ${e}"
            sendNotificationEvent("turnOn: SCREWED trying to schedule runIn --> ${e}")
        }
        sump_switch.on()
    
    } else {
        DEBUG("Too cold.  Not turnin nuttin on...")
    }
}

private def DEBUG(txt) {
    //log.debug ${app.label}
    log.debug(txt)
    if( settings.debugEnabled ) {
        sendNotificationEvent(txt)
    }
}