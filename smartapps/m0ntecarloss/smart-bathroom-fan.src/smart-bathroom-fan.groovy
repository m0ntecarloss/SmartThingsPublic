/**
 *  Smart Bathroom Fan
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
    name: "Smart Bathroom Fan",
    namespace: "m0ntecarloss",
    author: "Chris Roberts",
    description: "Work in progress.  Based on AirCycler switch with enhancements...",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

import java.text.SimpleDateFormat
import groovy.time.TimeCategory

preferences {

    // TODO: cleanup sections, descriptions, pages and stuff...
    // TODO: outdoor temp sensor / dew point type stuff
    section(hideable: true, "Debug") {
        input "debugEnabled", "bool", title: "Debug Enabled", required: true
    }
    
    section(hideable: true, "Hardware") {
        
        // bath fan switch to control smartly...
        input "fanSwitch",   "capability.switch", title: "Fan Switch", required: true
            
        // bath light switch to tie fan operation to
        input "lightSwitch", "capability.switch", title: "Light Switch", required: true
            
        // humidity sensor to override normal operation
        input "humiditySensor", "capability.relativeHumidityMeasurement", title: "Humidity Sensor", required: false
        
        // motion sensor to turn on fan and/or light
        input "motionSensor", "capability.motionSensor", title: "Motion Sensor", required: false
        
        // contact sensors to turn on the fan
        input "contactSensors", "capability.contactSensor", title: "Door/Window Sensors", required: false, multiple: true
    }
     
    section(hideable: true, "Controls or something") {
    
        input "minPerHour", "number", title: "Hourly Budget", description: "How many minutes per hour to run fan for", required: true
        
        input "minAfterLightOn", "number", title: "Minutes After Light On", description: "How many minutes after light turned on before fan turns on", required: false
     
        input "minAfterLightOff", "number", title: "Minutes After Light Off", description: "How many minutes after light turned off before fan turns off", required: false
    }
    
    section(hideable: true, "Scheduling and stuff") {
        paragraph "This section will be implemented when I feel like it"
    }

}

def installed() {
	DEBUG("installed")

	initialize()
}

def updated() {
    DEBUG("updated")

	unsubscribe()
	initialize()
}

def initialize() {
    def debug_string = new String()
    
    debug_string += "settings = ${settings}\n"
 
    state.turnOnScheduled  = false
    state.turnOffScheduled = false
    state.last_fan_on_time = now()
    state.total_fan_runtime_this_hour_in_seconds = 0.0
    
    // set up hourly schedule
    unschedule(hourlyHandler)
    runEvery1Hour(hourlyHandler)
    
	// TODO: subscribe to attributes, devices, locations, etc.
    
    subscribe (lightSwitch,    "switch.on",      lightHandler)
    subscribe (lightSwitch,    "switch.off",     lightHandler)
    subscribe (fanSwitch,      "switch.on",      fanOnHandler)
    subscribe (fanSwitch,      "switch.off",     fanOffHandler)
    subscribe (contactSensors, "contact.open",   contactOpenHandler)
	subscribe (contactSensors, "contact.closed", contactCloseHandler)
   
    // TODO: temporary
    hourlyHandler()
    
    DEBUG("initialize:\n ${debug_string}")
}

//------------------------------------------------------------------------------

def lightHandler(evt) {
    def debug_string = new String()
    
    debug_string += "light value: ${lightSwitch.currentSwitch}"
    DEBUG("lightHandler:\n ${debug_string}")
    
    if(lightSwitch.currentSwitch == "on") {
        //debug_string += "requesting fan on in ${minAfterLightOn} minutes\n"
        scheduleFanOn(minAfterLightOn)
    } else {
        //debug_string += "requesting fan off in ${minAfterLightOff} minutes\n"
        scheduleFanOff(minAfterLightOff)
    }
}

def scheduleFanOn(min_to_wait) {
    def debug_string  = new String()
    def ok_to_doit = true
   
    debug_string += "Checking if OK to schedule fan turn on\n"
    debug_string += "Min to wait:       ${min_to_wait}\n"
    debug_string += "Turn ON  Already scheduled: ${state.turnOnScheduled}\n"
    debug_string += "Turn OFF Already scheduled: ${state.turnOffScheduled}\n"
    
    if(state.turnOnScheduled) {
        debug_string += "We have already scheduled fan to turn off so no...\n"
        ok_to_doit = false
    }
  
    // check light sensors status
    debug_string += "Light Sensor is currently: ${lightSwitch.currentSwitch}\n"
    if(lightSwitch.currentSwitch == "on") {
    }
    
    // check contact sensors status
    for(xxx in contactSensors) {
        debug_string += "${xxx.device.displayName} is currently: ${xxx.currentContact}\n"
    }

    debug_string += "OK to turn on: ${ok_to_doit}\n"
    
    if(ok_to_doit) {
        debug_string += "OK SO DO IT\n"
        unschedule(turnFanOff)
        state.turnOffScheduled = false
        
        // check fan status before actually scheduling...
        debug_string += "Fan Switch is currently: ${fanSwitch.currentSwitch}\n"
        if(fanSwitch.currentSwitch == "on") {
            debug_string += "Hey fucker, the fan switch is already ${fanSwitch.currentSwitch}\n"
        } else if (min_to_wait > 0) {
            debug_string += "Scheduled fan to turn ON in ${min_to_wait} min"
            state.turnOnScheduled = true
            runIn(min_to_wait * 60, turnFanOn)
        } else {
            debug_string += "Turning fan ON immediately and stuff"
            turnFanOn()
        }
    }
    
   /*
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
    */
    
    DEBUG("scheduleFanOn:\n ${debug_string}")
}

def scheduleFanOff(min_to_wait) {
    def debug_string = new String()
    def ok_to_doit = true
    
    debug_string += "Checking if OK to schedule fan turn off\n"
    debug_string += "Min to wait:       ${min_to_wait}\n"
    debug_string += "Turn ON  Already scheduled: ${state.turnOnScheduled}\n"
    debug_string += "Turn OFF Already scheduled: ${state.turnOffScheduled}\n"
    
    if(state.turnOffScheduled) {
        debug_string += "We have already scheduled fan to turn off so no...\n"
        ok_to_doit = false
    }
  
    // check light sensors status
    debug_string += "Light switch is currently: ${lightSwitch.currentSwitch}\n"
    if(lightSwitch.currentSwitch == "on") {
        debug_string += "light sensor [${lightSwitch.device.displayName}] is still on so no...\n"
        ok_to_doit = false
    }
    
    // check contact sensors status
    for(xxx in contactSensors) {
        debug_string += "${xxx.device.displayName} is currently: ${xxx.currentContact}\n"
        if(xxx.currentContact == "open") {
            debug_string += "contact sensor [${xxx.device.displayName}] is still open so no...\n"
            ok_to_doit = false
        }
    }
  
    debug_string += "OK to turn off: ${ok_to_doit}\n"
    
    if(ok_to_doit) {
        debug_string += "OK SO DO IT\n"
        unschedule(turnFanOn)
        state.turnOnScheduled = false
        
        // check fan status before actually scheduling...
        debug_string += "Fan Switch is currently: ${fanSwitch.currentSwitch}\n"
        if(fanSwitch.currentSwitch == "off") {
            debug_string += "Hey fucker, the fan switch is already ${fanSwitch.currentSwitch}\n"
        } else if(min_to_wait > 0) {
            state.turnOffScheduled = true
            debug_string += "Scheduled fan to turn OFF in ${min_to_wait} min"
            runIn(min_to_wait * 60, turnFanOff)
        } else {
            debug_string += "Turning fan OFF immediately and stuff"
            turnFanOff()
        }
    }
        
    DEBUG("scheduleFanOff:\n ${debug_string}")
}

def fanOnHandler(evt) {
    def debug_string = new String()
    
    debug_string += "fanOnHandler:\n"
    
    state.last_fan_on_time = now()
    
    unschedule(turnFanOff)
    state.turnOffScheduled = false
    
    unschedule(turnFanOn)
    state.turnOnScheduled  = false
    
    DEBUG(debug_string)
}

def fanOffHandler(evt) {
    def debug_string = new String()
    
    debug_string += "fanOffHandler:\n"
    
    def fanRuntimeSeconds = (now() - state.last_fan_on_time) / 1000.0
    
    state.total_fan_runtime_this_hour_in_seconds = state.total_fan_runtime_this_hour_in_seconds + fanRuntimeSeconds
    debug_string += "Fan was on for ${fanRuntimeSeconds} seconds.  Total time this hour = ${state.total_fan_runtime_this_hour_in_seconds}\n"
        
    unschedule(turnFanOff)
    state.turnOffScheduled = false
    
    DEBUG(debug_string)
}

def contactOpenHandler(evt) {
    def debug_string  = new String()
    debug_string += "requesting fan on immediately\n"
    scheduleFanOn(0)
    DEBUG("contactOpenHandler:\n ${debug_string}")
}

def contactCloseHandler(evt) {
    def debug_string  = new String()
    debug_string += "requesting fan off in ${minAfterLightOff} minutes\n"
    scheduleFanOff(minAfterLightOff)
    DEBUG("contactCloseHandler:\n ${debug_string}")
}

def turnFanOn() {
    DEBUG("turnFanOn")
    fanSwitch.on()
}

def turnFanOff() {
    DEBUG("turnFanOff")
    // TODO: Check if we really should for realz turn off the fan now
    fanSwitch.off()
}

//------------------------------------------------------------------------------
def hourlyHandler(evt) {
    
    // TODO: This whole thing is no good.  We don't want to check after an hour is over to see
    //       how much time the fan SHOULD have been on in that hour.
    //
    //       What really needs to happen is we need to schedule the check
    //       at the beginning of the hour at the latest possible time
    //       we should turn the fan on in that hour.  Then each time
    //       the fan turns off we should unschedule/reschedule accordingly
    
    def debug_string = new String()
    def counter      = 0
    def cur          = new Date()
    def hour_ago     = new Date()
    def total_secs   = 0
    use(TimeCategory) {
        hour_ago = hour_ago - 1.hour
    }
    def last_event  = hour_ago
    
    debug_string += "--------------------------\n"
    debug_string += "current time = ${cur}\n"
    debug_string += "last hour    = ${hour_ago}\n"
    
    // TODO: need to check for and handle repeated events in the
    //       logic (i.e. two off events in a row for whatever reason)
    for(zzz in fanSwitch.eventsSince(hour_ago).reverse()) {
        if(zzz.value == "on" || zzz.value == "off") {
            counter += 1
            debug_string += "--------------------------\n"
            debug_string += "EVENT: ${counter}\n"
            debug_string += "       date            = ${zzz.date}\n"
            //debug_string += "       name            = ${zzz.name}\n"
            debug_string += "       device          = ${zzz.device.displayName}\n"
            debug_string += "       description     = ${zzz.description}\n"
            //debug_string += "       descriptionText = ${zzz.descriptionText}\n"
            debug_string += "       state_change    = ${zzz.isStateChange()}\n"
            //debug_string += "       physical        = ${zzz.isPhysical()}\n"
            debug_string += "       value           = ${zzz.value}\n"
            debug_string += "       source          = ${zzz.source}\n"
          
            if(zzz.value == "off") {
                def seconds_since_last_mark = (zzz.date.getTime() - last_event.getTime()) / 1000
                total_secs += seconds_since_last_mark
                debug_string += "       seconds since l = ${seconds_since_last_mark}\n"
            }
            
            last_event = zzz.date
        }
    }
    // If fan switch is still on, then reset the last on time to now since we've already
    // addressed this past hours runtime
    if(fanSwitch.currentSwitch == "on") {
        def fanRuntimeSeconds = (now() - state.last_fan_on_time) / 1000.0
        state.total_fan_runtime_this_hour_in_seconds = state.total_fan_runtime_this_hour_in_seconds + fanRuntimeSeconds
        
        debug_string += "Fan switch is still on so resetting last on time to now..."
        state.last_fan_on_time = now()
    } else {
        debug_string += "Fan switch is off.  No need to mess with last fan on time..."
    }
    
    debug_string += "TOTAL ON TIME: ${total_secs}\n"
    debug_string += "TOTAL ON TIME NEW WAY: ${state.total_fan_runtime_this_hour_in_seconds}\n"
   
    state.total_fan_runtime_this_hour_in_seconds = 0
    
    DEBUG("hourlyHandler:\n ${debug_string}")
}

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
private def DEBUG(txt) {
    //log.debug ${app.label}
    log.debug(txt)
    if( debugEnabled ) {
        sendNotificationEvent("Smart Bathroom Fan: " + txt)
    }
}