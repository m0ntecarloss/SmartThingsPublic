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
    
        input "fanMinPerHour", "number", title: "Hourly Budget", description: "How many minutes per hour to run fan for", required: true
        
        input "minAfterLightOn", "number", title: "Minutes After Light On", description: "How many minutes after light turned on before fan turns on", required: false
     
        input "minAfterLightOff", "number", title: "Minutes After Light Off", description: "How many minutes after light turned off before fan turns off", required: false
    }
    
    section(hideable: true, "Scheduling and stuff") {
        paragraph "This section will be implemented when I feel like it"
    }

}

//------------------------------------------------------------------------------

def installed() {
	DEBUG("installed")
	initialize()
}

//------------------------------------------------------------------------------

def updated() {
    DEBUG("updated")
	initialize()
}

//------------------------------------------------------------------------------

def initialize() {
    String debug_string = "initialize:\n"
    
    debug_string += "settings = ${settings}\n"
 
    state.turnOnScheduled                        = false
    state.turnOffScheduled                       = false
    state.last_fan_on_time                       = now()
    state.this_hour_start_time                   = now()
    state.total_fan_runtime_this_hour_in_seconds = 0.0
    state.fan_locked                             = true
    state.time_left_in_quota                     = 0
    //state.total_hours_logged                     = -1 // Because we call hourlyHandler in this routine which isn't quite right...
    state.total_hours_logged                     = 0 // Because we call hourlyHandler in this routine which isn't quite right...
    state.total_runtime_minutes_forever          = 0
    state.average_min_per_hour                   = 0.0

    //def xxx = new Date()
    //def yyy = xxx.getTimezoneOffset()
    //def wtf = xxx.getHours() - (xxx.getTimezoneOffset() / 60)
    //debug_string += "yyy: ${yyy}\n"
    //debug_string += "wtf: ${wtf}\n"
    //def fart = Calendar.getInstance(location.timeZone, Locale.US)
    //debug_string += "fart " + fart.format("yyyy/MM/dd HH:mm:ss") + " ${fart.HOUR}\n"
    
    // set up hourly schedule
    unschedule()
    //unschedule(hourlyHandler)
    //unschedule(turnFanOnForQuota)
    //runEvery1Hour(hourlyHandler)
    //schedule("15 01 * * * ?", hourlyHandler)
    schedule("1 2 * * * ?", hourlyHandler)
    
    // Temp
    //runEvery5Minutes(oldSchoolDump)
    //runEvery1Minute(oldSchoolDump)
   
	unsubscribe()
    subscribe (lightSwitch,    "switch.on",      lightHandler)
    subscribe (lightSwitch,    "switch.off",     lightHandler)
    subscribe (fanSwitch,      "switch.on",      fanOnHandler)
    subscribe (fanSwitch,      "switch.off",     fanOffHandler)
    subscribe (contactSensors, "contact.open",   contactOpenHandler)
	subscribe (contactSensors, "contact.closed", contactCloseHandler)
   
    DEBUG(debug_string)
    
    // TODO: temporary
    //oldSchoolDump()
    //DEBUG("Calling hourlyHandler as part of setup...\n")
    //hourlyHandler()
    //runIn(180, hourlyHandler)
}

//------------------------------------------------------------------------------

def lightHandler(evt) {
    String debug_string = "lightHandler:\n"
    
    debug_string += "light value: ${lightSwitch.currentSwitch}"
    DEBUG(debug_string)
    
    if(lightSwitch.currentSwitch == "on") {
        scheduleFanOn(minAfterLightOn)
    } else {
        scheduleFanOff(minAfterLightOff)
    }
}

//------------------------------------------------------------------------------

def scheduleFanOn(min_to_wait) {
    String debug_string = "scheduleFanOn:\n"
    def ok_to_doit = true
   
    debug_string += "    Checking if OK to schedule fan turn ON\n"
    debug_string += "    Min to wait:                ${min_to_wait}\n"
    debug_string += "    Turn ON  Already scheduled: ${state.turnOnScheduled}\n"
    debug_string += "    Turn OFF Already scheduled: ${state.turnOffScheduled}\n"
    debug_string += "    Fan locked on:              ${state.fan_locked}\n"
    
    if(state.turnOnScheduled) {
        debug_string += "    We have already scheduled fan to turn on so no...\n"
        ok_to_doit = false
    }
  
    // check light sensors status
    debug_string += "    Light Sensor is currently: ${lightSwitch.currentSwitch}\n"
    if(lightSwitch.currentSwitch == "on") {
    }
    
    // check contact sensors status
    for(xxx in contactSensors) {
        debug_string += "    ${xxx.device.displayName} is currently: ${xxx.currentContact}\n"
    }

    debug_string += "    Fan Switch is currently: ${fanSwitch.currentSwitch}\n"
    debug_string += "    OK to turn on: ${ok_to_doit}\n"
    
    if(ok_to_doit) {
        debug_string += "    OK SO DO IT\n"
        
        state.turnOffScheduled = false
        state.turnOnScheduled  = false
        unschedule(turnFanOff)
        unschedule(turnFanOn)
        
        // check fan status before actually scheduling...
        if(fanSwitch.currentSwitch == "on") {
            debug_string += "    The fan switch is already ${fanSwitch.currentSwitch}\n"
        } else if (min_to_wait > 0) {
            debug_string += "    Scheduled fan to turn ON in ${min_to_wait} min"
            state.turnOnScheduled = true
            runIn(min_to_wait * 60, turnFanOn)
        } else {
            debug_string += "    Turning fan ON immediately and stuff"
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
    
    DEBUG(debug_string)
}

//------------------------------------------------------------------------------

def scheduleFanOff(min_to_wait) {
    String debug_string = "scheduleFanOff:\n"
    def ok_to_doit = true
    
    debug_string += "    Checking if OK to schedule fan turn OFF\n"
    debug_string += "    Min to wait:                ${min_to_wait}\n"
    debug_string += "    Turn ON  Already scheduled: ${state.turnOnScheduled}\n"
    debug_string += "    Turn OFF Already scheduled: ${state.turnOffScheduled}\n"
    debug_string += "    Fan locked on:              ${state.fan_locked}\n"
   
    if(state.fan_locked) {
        debug_string += "    Fan is locked on for hourly quota so no...\n"
        ok_to_doit = false
    }
    
    if(state.turnOffScheduled) {
        debug_string += "    We have already scheduled fan to turn off so no...\n"
        ok_to_doit = false
    }
  
    // check light sensors status
    debug_string += "    Light switch is currently: ${lightSwitch.currentSwitch}\n"
    if(lightSwitch.currentSwitch == "on") {
        debug_string += "    light sensor [${lightSwitch.device.displayName}] is still on so no...\n"
        ok_to_doit = false
    }
    
    // check contact sensors status
    for(xxx in contactSensors) {
        debug_string += "    ${xxx.device.displayName} is currently: ${xxx.currentContact}\n"
        if(xxx.currentContact == "open") {
            debug_string += "    contact sensor [${xxx.device.displayName}] is still open so no...\n"
            ok_to_doit = false
        }
    }
    
    debug_string += "    Fan Switch is currently: ${fanSwitch.currentSwitch}\n"
    debug_string += "    OK to turn off: ${ok_to_doit}\n"
    
    if(ok_to_doit) {
        debug_string += "    OK SO DO IT\n"
        
        state.turnOnScheduled  = false
        state.turnOffScheduled = false
        unschedule(turnFanOn)
        unschedule(turnFanOff)
        
        // check fan status before actually scheduling...
        if(fanSwitch.currentSwitch == "off") {
            debug_string += "    The fan switch is already ${fanSwitch.currentSwitch}\n"
        } else if(min_to_wait > 0) {
            debug_string += "    Scheduled fan to turn OFF in ${min_to_wait} min"
            state.turnOffScheduled = true
            runIn(min_to_wait * 60, turnFanOff)
        } else {
            debug_string += "    Turning fan OFF immediately and stuff"
            turnFanOff()
        }
    }
        
    DEBUG(debug_string)
}

//------------------------------------------------------------------------------

def fanOnHandler(evt) {
    String debug_string = "fanOnHandler:\n"
    
    state.last_fan_on_time = now()
    state.turnOffScheduled = false
    state.turnOnScheduled  = false
    
    unschedule(turnFanOff)
    unschedule(turnFanOn)
    
    DEBUG(debug_string)
}

//------------------------------------------------------------------------------

def fanOffHandler(evt) {
    String debug_string = "fanOffHandler:\n"
    Integer total_fan_runtime_this_hour_seconds = 0
    Integer total_fan_runtime_this_hour_minutes = 0
    Integer time_left_in_quota_seconds          = 0
    Integer time_left_in_quota_minutes          = 0
  
    def fanRuntimeSeconds = (now() - state.last_fan_on_time) / 1000.0
    state.total_fan_runtime_this_hour_in_seconds = state.total_fan_runtime_this_hour_in_seconds + fanRuntimeSeconds
    
    total_fan_runtime_this_hour_minutes = state.total_fan_runtime_this_hour_in_seconds / 60
    total_fan_runtime_this_hour_seconds = state.total_fan_runtime_this_hour_in_seconds - (total_fan_runtime_this_hour_minutes * 60)
    
    time_left_in_quota_minutes          = state.time_left_in_quota / 60
    time_left_in_quota_seconds          = state.time_left_in_quota - (time_left_in_quota_minutes * 60)
    
    debug_string += "    Fan runtime was:             ${fanRuntimeSeconds} sec\n"
    debug_string += "    Total time this hour in:     ${state.total_fan_runtime_this_hour_in_seconds} sec\n"
    debug_string += "    Total fan runtime this hour: ${total_fan_runtime_this_hour_minutes} min ${total_fan_runtime_this_hour_seconds} sec\n"
    debug_string += "    Time left in quota:          ${time_left_in_quota_minutes} min ${time_left_in_quota_seconds} sec\n"
        
    state.turnOffScheduled = false
    state.turnOnScheduled  = false
    state.fan_locked       = false
    
    unschedule(turnFanOff)
    unschedule(turnFanOn)
    
    DEBUG(debug_string)
   
    // See if we need to reschedule a time to turn
    // fan back on to meet hourly quota
    schedule_next_fan_on()
}

//------------------------------------------------------------------------------

def contactOpenHandler(evt) {
    String debug_string = "contactOpenHandler:\n"
    debug_string += "    requesting fan on immediately\n"
    DEBUG(debug_string)
    scheduleFanOn(0)
}

//------------------------------------------------------------------------------

def contactCloseHandler(evt) {
    String debug_string = "contactCloseHandler:\n"
    debug_string += "    requesting fan immediately for door close\n"
    DEBUG(debug_string)
    //scheduleFanOff(minAfterLightOff)
    scheduleFanOff(0)
}

//------------------------------------------------------------------------------

def turnFanOn() {
    String debug_string = "turnFanOn:\n"
    fanSwitch.on()
/*
    try {
        debug_string += "sending poll command to switches...\n"
        fanSwitch.refresh(2000)
        lightSwitch.refresh(2000)
    } catch (e) {
        debug_string += "oops\n"
        log.debug("oops", e)
    }
*/
    DEBUG(debug_string)
}

//------------------------------------------------------------------------------

def turnFanOff() {
    String debug_string = "turnFanOff:\n"
    fanSwitch.off()
/*
    try {
        debug_string += "sending poll command to switches...\n"
        fanSwitch.refresh(2000)
        lightSwitch.refresh(2000)
    } catch (e) {
        debug_string += "oops\n"
        log.debug("oops", e)
    }
*/
    DEBUG(debug_string)
}

//------------------------------------------------------------------------------

def turnFanOnForQuota() {
    String debug_string = "turnFanOnForQuota:\n"
    Integer total_fan_runtime_this_hour_minutes = state.total_fan_runtime_this_hour_in_seconds / 60
    Integer time_left_in_quota_minutes          = state.time_left_in_quota / 60
    debug_string += "    Time needed to run in seconds       = ${state.time_left_in_quota}\n"
    debug_string += "    Time needed to run in minutes       = ${time_left_in_quota_minutes}\n"
    debug_string += "    Total fan runtime this hour seconds = ${state.total_fan_runtime_this_hour_in_seconds}\n"
    debug_string += "    Total fan runtime this hour minutes = ${total_fan_runtime_this_hour_minutes}\n"
    state.fan_locked = true
    fanSwitch.on()
/*
    try {
        debug_string += "sending poll command to switches...\n"
        fanSwitch.refresh(2000)
        lightSwitch.refresh(2000)
    } catch (e) {
        debug_string += "oops\n"
        log.debug("oops", e)
    }
*/
    DEBUG(debug_string)
}

//------------------------------------------------------------------------------

def hourlyHandler(evt) {
    
    String debug_string = "hourlyHandler:\n"
    
    try {
    
        // If fan switch is still on, then reset the last on time to now since we've already
        // addressed this past hours runtime
        if(fanSwitch.currentSwitch == "on") {
            def curr_time = now()
            debug_string += "  Fan switch is still on so resetting last on time to now...\n"
            debug_string += "    last_fan_on_time = ${state.last_fan_on_time}\n"
            debug_string += "    now()            = ${curr_time}\n"
        
            def fanRuntimeSeconds = (now() - state.last_fan_on_time) / 1000.0
            state.total_fan_runtime_this_hour_in_seconds = state.total_fan_runtime_this_hour_in_seconds + fanRuntimeSeconds
            state.last_fan_on_time = now()
        } else {
            debug_string += "  Fan switch is off.  No need to mess with last fan on time...\n"
        }
 
        Integer minutes = state.total_fan_runtime_this_hour_in_seconds / 60
        Integer seconds = state.total_fan_runtime_this_hour_in_seconds - (minutes * 60)
    
        debug_string += "  HOURLY: Total runtime = ${state.total_fan_runtime_this_hour_in_seconds}\n"
        debug_string += "  HOURLY: Total runtime = ${minutes} minutes ${seconds} seconds\n"
   
        state.total_hours_logged = state.total_hours_logged + 1
        if(state.total_hours_logged > 0) {
            state.total_runtime_minutes_forever = state.total_runtime_minutes_forever + (state.total_fan_runtime_this_hour_in_seconds / 60)
            state.average_min_per_hour = state.total_runtime_minutes_forever / state.total_hours_logged
            debug_string += "  Average hourly runtime:\n"
            debug_string += "    Total hours logged: ${state.total_hours_logged}\n"
            debug_string += "    Total minutes ran:  ${state.total_runtime_minutes_forever}\n"
            debug_string += "    AVERAGE PER HOUR:   ${state.average_min_per_hour} min/hr\n"
        }
 
        if(state.fan_locked) {
            debug_string += "  Fan was on to meet quota.  It's OK to turn off now...\n"
            state.fan_locked = false
            scheduleFanOff(0)
        }
    
        // Clear out data for a new hour
        state.total_fan_runtime_this_hour_in_seconds = 0
        state.this_hour_start_time                   = now()
        debug_string += "  Fan runtime this hour reset to 0\n"
  
        // Schedule fan turn on time in case nothing else
        // triggers the fan to turn on so we can meet
        // the hourly quota
        schedule_next_fan_on()
    
    } catch(e) {
        DEBUG("  DARN.  bad stuff in hourly handler...\n${e}\n")
    }
    DEBUG(debug_string)
    
    oldSchoolDump()
}
 
 
def oldSchoolDump() {
    String debug_string = "Old School Dump:\n"
    
    try {
        Integer counter      = 0
        Integer total_secs   = 0
        Integer total_mins   = 0
        def     cur          = new Date()
        def     hour_ago     = new Date()
        use(TimeCategory) {
            hour_ago = hour_ago - 1.hour
        }
        def last_event       = hour_ago
        def last_event_value = "unknown"

        def df = new java.text.SimpleDateFormat("EEE MMM dd 'at' hh:mm:ss a")
        df.setTimeZone(location.timeZone)
        
        def formatted_current_time = df.format(cur)
        def formatted_last_hour    = df.format(hour_ago)
        
        debug_string += "--------------------------\n"
        debug_string += "---- Old School Stuff ----\n"
        debug_string += "--------------------------\n"
        //debug_string += "  current time = ${cur}\n"
        debug_string += "  current time = ${formatted_current_time}\n"
        //debug_string += "  last hour    = ${hour_ago}\n"
        debug_string += "  last hour    = ${formatted_last_hour}\n"

        for(zzz in fanSwitch.eventsSince(hour_ago).reverse()) {
            if(zzz.value == "on" || zzz.value == "off") {
            
                def formattedDate = df.format(zzz.date)

                counter += 1
                debug_string += "   EVENT: ${counter}\n"
                debug_string += "     date            = ${zzz.date}\n"
                debug_string += "     date            = ${formattedDate}\n"
                //debug_string += "     name            = ${zzz.name}\n"
                //debug_string += "     device          = ${zzz.device.displayName}\n"
                //debug_string += "     description     = ${zzz.description}\n"
                debug_string += "     descriptionText = ${zzz.descriptionText}\n"
                //debug_string += "     state_change    = ${zzz.isStateChange()}\n"
                //debug_string += "     physical        = ${zzz.isPhysical()}\n"
                debug_string += "     value           = ${zzz.value}\n"
                debug_string += "     last value      = ${last_event_value}\n"
                debug_string += "     source          = ${zzz.source}\n"

                if(zzz.value == "off") {
                    if(last_event_value == zzz.value) {
                        debug_string += "     Last event was off so not counting...\n"
                    } else {
                        def seconds_since_last_mark = (zzz.date.getTime() - last_event.getTime()) / 1000
                        total_secs += seconds_since_last_mark
                        debug_string += "     seconds since last = ${seconds_since_last_mark}\n"
                        debug_string += "     Total secs         = ${total_secs}\n"
                    }
                }

                last_event       = zzz.date
                last_event_value = zzz.value
            }
        }
       
        total_mins = total_secs / 60
        total_secs = total_secs - (total_mins * 60)
   
        debug_string += "  Total fan runtime last hour: ${total_mins} mins and ${total_secs} secs\n"
        
        debug_string += "--------------------------\n"
        debug_string += "-- End Old School Stuff --\n"
        debug_string += "--------------------------\n"
        
    } catch(e) {
        DEBUG("  DARN.  bad stuff in hourly handler old school stuff...\n${e}\n")
    }
    
    DEBUG(debug_string)
}

//------------------------------------------------------------------------------

private def schedule_next_fan_on() {

    String debug_string = "schedule_next_fan_on:\n"
   
    if(fanMinPerHour <= 0) {
        debug_string += "fanMinPerHour value [${fanMinPerHour}] is invalid or 0.  Not doing hourly budget...\n"
    } else {
        def runtime_left_secs         = (fanMinPerHour * 60) - state.total_fan_runtime_this_hour_in_seconds
        def hour_start_epoch          = state.this_hour_start_time / 1000.0
        def hour_end_epoch            = hour_start_epoch + 3600
        def next_time_to_run_epoch    = hour_end_epoch - runtime_left_secs
        def curr_time_epoch           = now() / 1000.0
        def run_in_secs               = next_time_to_run_epoch - curr_time_epoch
        def seconds_left_in_this_hour = hour_end_epoch - curr_time_epoch
     
        debug_string += "runtime_left_secs         = ${runtime_left_secs}\n"
        debug_string += "hour_start_epoch          = ${hour_start_epoch}\n"
        debug_string += "hour_end_epoch            = ${hour_end_epoch}\n"
        debug_string += "next_time_to_run_epoch    = ${next_time_to_run_epoch}\n"
        debug_string += "curr_time_epoch           = ${curr_time_epoch}\n"
        debug_string += "run_in_secs               = ${run_in_secs}\n"
        debug_string += "seconds_left_in_this_hour = ${seconds_left_in_this_hour}\n"
    
        def fudge = 0.0
        if(state.average_min_per_hour > fanMinPerHour + 1) {
            fudge = 240.0
            debug_string += "Average minutes per hour ${state.average_min_per_hour} > ${fanMinPerHour + 1}.\n"
        } else if (state.average_min_per_hour > fanMinPerHour) {
            fudge = (state.average_min_per_hour - ${fanMinPerHour}) * 60
            debug_string += "Average minutes per hour ${state.average_min_per_hour} > ${fanMinPerHour} by less than 1 minute\n"
        }
        debug_string += "Fudge factor: ${fudge}\n"
        run_in_secs = run_in_secs + fudge
        debug_string += "New run_in_secs = ${run_in_secs}\n"
        
        if(runtime_left_secs <= 0) {
            debug_string += "Looks like we used up our quota for this hour because runtime_left_secs <= 0.  Good job!  No need to reschedule.\n"
            unschedule(turnFanOnForQuota)
        } else {
            debug_string += "Rescheduling to run in ${run_in_secs} seconds to make sure we meet our quota...\n"
            state.time_left_in_quota = runtime_left_secs
            runIn(run_in_secs, turnFanOnForQuota)
        }
    }
    
    DEBUG(debug_string)
}

//------------------------------------------------------------------------------

private def DEBUG(txt) {
    //log.debug ${app.label}
    log.debug(txt)
    if( debugEnabled ) {
        sendNotificationEvent("Smart Bathroom Fan: " + txt)
    }
}
//------------------------------------------------------------------------------

/*
def OLDhourlyHandler(evt) {
    
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
    
    DEBUG("hourlyHandler:\n ${debug_string}")
}
*/
