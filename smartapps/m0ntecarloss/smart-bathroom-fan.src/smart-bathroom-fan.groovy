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
    
        input "fanMinPerHour",    "number", title: "Hourly Budget",                    description: "How many minutes per hour to run fan for",                     required: true
        input "minAfterLightOn",  "number", title: "Minutes After Light On",           description: "How many minutes after light turned on before fan turns on",   required: false
        input "minAfterLightOff", "number", title: "Minutes After Light Off",          description: "How many minutes after light turned off before fan turns off", required: false
        input "fudgePercent",     "number", title: "Fudge Percentage",                 description: "Amount to fudge",                                              required: false
        input "maxFanRuntime",    "number", title: "Max number of minutes to run fan", description: "Max number of minutes to run fan",                             required: false
        input "minFanRuntime",    "number", title: "Min number of minutes to run fan", description: "Min number of minutes to run fan",                             required: false
    }
    
    section(hideable: true, "Scheduling and stuff") {
        paragraph "This section will be implemented when I feel like it"
    }

    section(hideable: false, "Stats and Junk") {
    
        String stats_string = ""
   
        try {
            Integer minutes = state.total_fan_runtime_this_hour_in_seconds / 60
            Integer seconds = state.total_fan_runtime_this_hour_in_seconds - (minutes * 60)
        } catch(e) {
            Integer minutes = 0
            Integer seconds = 0
        }
    
        stats_string += "  HOURLY: Total runtime = ${minutes} minutes ${seconds} seconds\n"
        stats_string += "  Average hourly runtime:\n"
        stats_string += "    Total hours logged: ${total_hours_logged}\n"
        stats_string += "    Total minutes ran:  ${total_runtime_minutes_forever}\n"
        stats_string += "    AVERAGE PER HOUR:   ${average_min_per_hour} min/hr\n"
        
        paragraph "${stats_string}"
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
    
    if(settings.fanMinPerHour <= 0) {
        settings.fanMinPerHour = 0
        debug_string += "Invalid fanMinPerHour.  Using ${settings.fanMinPerHour}\n"
    }
    if(maxFanRuntime <= 0) {
        settings.maxFanRuntime = 60
        debug_string += "Invalid maxFanRuntime.  Using ${settings.maxFanRuntime}\n"
    }
    if(minFanRuntime <= 0) {
        settings.minFanRuntime = 0
        debug_string += "Invalid minFanRuntime.  Using ${settings.minFanRuntime}\n"
    }
    if(fudgePercent <= 0) {
        settings.fudgePercent = 5
        debug_string += "Invalid fudgePercent.  Using ${settings.fudgePercent}\n"
    }
    
    debug_string += "settings = ${settings}\n"
 
    state.turnOnScheduled                        = false
    state.turnOffScheduled                       = false
    state.last_fan_on_time                       = now()
    state.this_hour_start_time                   = now()
    state.total_fan_runtime_this_hour_in_seconds = 0.0
    state.fan_locked                             = false
    state.time_left_in_quota                     = 0
    state.total_hours_logged                     = 0
    state.total_runtime_minutes_forever          = 0
    state.average_min_per_hour                   = 0.0
    state.min_fan_runtime_secs                   = 9999999.0
    state.max_fan_runtime_secs                   = 0.0

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
    schedule("1 02 * * * ?", hourlyHandler)
    
    // Temp
    //runEvery5Minutes(oldSchoolDump)
    //runEvery1Minute(oldSchoolDump)
    //schedule("1 03 * * * ?", oldSchoolDump)
   
	unsubscribe()
    subscribe (lightSwitch,    "switch.on",      handlerLightOnOff)
    subscribe (lightSwitch,    "switch.off",     handlerLightOnOff)
    subscribe (fanSwitch,      "switch.on",      handlerFanOn)
    subscribe (fanSwitch,      "switch.off",     handlerFanOff)
    subscribe (contactSensors, "contact.open",   handlerContactOpen)
	subscribe (contactSensors, "contact.closed", handlerContactClosed)
   
    DEBUG(debug_string)
    
    // TODO: temporary
    //oldSchoolDump()
    oldSchoolDump24()
    //DEBUG("Calling hourlyHandler as part of setup...\n")
    //hourlyHandler()
    //runIn(180, hourlyHandler)
}

//------------------------------------------------------------------------------

def handlerLightOnOff(evt) {
    String debug_string = "handlerLightOnOff:\n"
    
    debug_string += "light value: ${lightSwitch.currentSwitch}"
    
    if(lightSwitch.currentSwitch == "on") {
        DEBUG(debug_string)
        state.lightOnTime = now()
        scheduleFanOn(minAfterLightOn)
    } else {
        Integer totalRuntimeSeconds = (now() - state.lightOnTime) / 1000.0
        Integer totalRuntimeMinutes = totalRuntimeSeconds / 60
        
        debug_string += " Was on for ${secToMinSecString(totalRuntimeSeconds)}\n"
        DEBUG(debug_string)
        
        if(totalRuntimeMinutes < minAfterLightOff)
            scheduleFanOff(totalRuntimeMinutes)
        else
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
        unschedule(turnFanOff)
        
        state.turnOnScheduled = false
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
        
        state.turnOnScheduled = false
        unschedule(turnFanOn)
        
        state.turnOffScheduled = false
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

def handlerFanOn(evt) {
    String debug_string = "handlerFanOn:\n"
    
    state.last_fan_on_time = now()
    
    state.turnOffScheduled = false
    unschedule(turnFanOff)
    
    state.turnOnScheduled = false
    unschedule(turnFanOn)
    
    DEBUG(debug_string)
}

//------------------------------------------------------------------------------

def handlerFanOff(evt) {
    String debug_string = "handlerFanOff:\n"
  
    def fanRuntimeSeconds = (now() - state.last_fan_on_time) / 1000.0
  
    if(fanRuntimeSeconds < state.min_fan_runtime_secs)
        state.min_fan_runtime_secs = fanRuntimeSeconds
        
    if(fanRuntimeSeconds > state.max_fan_runtime_secs)
        state.max_fan_runtime_secs = fanRuntimeSeconds
    
    state.total_fan_runtime_this_hour_in_seconds = state.total_fan_runtime_this_hour_in_seconds + fanRuntimeSeconds
    
    debug_string += "    Fan runtime was:             ${secToMinSecString(fanRuntimeSeconds)}\n"
    debug_string += "    Total fan runtime this hour: ${secToMinSecString(state.total_fan_runtime_this_hour_in_seconds)}\n"
    debug_string += "    Min Fan Runtime:             ${secToMinSecString(state.min_fan_runtime_secs)}\n"
    debug_string += "    Max Fan Runtime:             ${secToMinSecString(state.max_fan_runtime_secs)}\n"
        
    state.fan_locked = false
    
    state.turnOffScheduled = false
    unschedule(turnFanOff)
    
    state.turnOnScheduled = false
    unschedule(turnFanOn)
    
    DEBUG(debug_string)
   
    // See if we need to reschedule a time to turn
    // fan back on to meet hourly quota
    scheduleNextFanOn()
}

//------------------------------------------------------------------------------

def handlerContactOpen(evt) {
    String debug_string = "handlerContactOpen:\n"
    debug_string += "    requesting fan on immediately\n"
    DEBUG(debug_string)
    scheduleFanOn(0)
}

//------------------------------------------------------------------------------

def handlerContactClosed(evt) {
    String debug_string = "handlerContactClosed:\n"
    debug_string += "    requesting fan immediately for door close\n"
    DEBUG(debug_string)
    //scheduleFanOff(minAfterLightOff)
    scheduleFanOff(0)
}

//------------------------------------------------------------------------------

def turnFanOn() {
    String debug_string = "turnFanOn:\n"
    fanSwitch.on()
    DEBUG(debug_string)
}

//------------------------------------------------------------------------------

def turnFanOff() {
    String debug_string = "turnFanOff:\n"
    fanSwitch.off()
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
        log.debug "hourlyHandler calling scheduleNextFanOn"
        scheduleNextFanOn()
        log.debug "hourlyHandler returned from scheduleNextFanOn"
    
    } catch(e) {
        log.debug "hourlyHandler had an exception :("
        debug_string += "DARN.  Unhandled exception:\n${e}\n"
    }
    DEBUG(debug_string)
    
    //oldSchoolDump()
    oldSchoolDump24()
}
 
 
//------------------------------------------------------------------------------

def scheduleNextFanOn() {

    String debug_string   = "scheduleNextFanOn:\n"
   
    //def df = new java.text.SimpleDateFormat("EEE MMM dd 'at' hh:mm:ss a")
    def df = new java.text.SimpleDateFormat("hh:mm:ss a")
    df.setTimeZone(location.timeZone)
        
    try {
        if(fanMinPerHour <= 0) {
            debug_string += "fanMinPerHour value [${fanMinPerHour}] is invalid or 0.  Not doing hourly budget...\n"
        } else {
            def runtime_left_secs         = (fanMinPerHour * 60) - state.total_fan_runtime_this_hour_in_seconds
            def hour_start_secs           = state.this_hour_start_time / 1000.0
            def hour_end_secs             = hour_start_secs + 3600
            def next_runtime_secs         = hour_end_secs - runtime_left_secs
            def curr_time_secs            = now() / 1000.0
            def run_in_secs               = next_runtime_secs - curr_time_secs
            def seconds_left_in_this_hour = hour_end_secs - curr_time_secs
           
            def hour_start_formatted      = df.format(hour_start_secs   * 1000.0)
            def hour_end_formatted        = df.format(hour_end_secs     * 1000.0)
            def curr_time_formatted       = df.format(curr_time_secs    * 1000.0)
            def next_runtime_formatted    = df.format(next_runtime_secs * 1000.0)

            debug_string += "runtime_left_secs         = ${runtime_left_secs}\n"
            debug_string += "hour_start_secs           = ${hour_start_secs}   (${hour_start_formatted})\n"
            debug_string += "hour_end_secs             = ${hour_end_secs}     (${hour_end_formatted})\n"
            debug_string += "next_runtime_secs         = ${next_runtime_secs} (${next_runtime_formatted})\n"
            debug_string += "curr_time_secs            = ${curr_time_secs}    (${curr_time_formatted})\n"
            debug_string += "run_in_secs               = ${run_in_secs}\n"
            debug_string += "seconds_left_in_this_hour = ${seconds_left_in_this_hour}\n"
           

            /* 
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
            */
           
            def better_fudge = 0.0
            better_fudge                    = (state.average_min_per_hour - fanMinPerHour) * 60.0 * 1.05
            debug_string += "better_fudge A            = ${better_fudge}\n"
            better_fudge                    = (state.average_min_per_hour - fanMinPerHour) * 60.0 * (1 + (fudgePercent / 100.0))
            better_fudge                    = (state.average_min_per_hour - fanMinPerHour) * 60.0 * (1 + (50 / 100.0))
            debug_string += "better_fudge B            = ${better_fudge}\n"
            if(better_fudge < 0) {
                better_fudge = 0
            }
            debug_string += "better_fudge C            = ${better_fudge}\n"
            run_in_secs                     = run_in_secs + better_fudge
            def next_runtime_real_formatted = df.format(now() + run_in_secs * 1000.0)
            debug_string += "average_min_per_hour      = ${state.average_min_per_hour}\n"
            debug_string += "desired_min_per_hour      = ${fanMinPerHour}\n"
            debug_string += "New run_in_secs           = ${run_in_secs}\n"
            debug_string += "Real next runtime         = ${next_runtime_real_formatted}\n"

            if(runtime_left_secs <= 0 || run_in_secs < 0) {
                debug_string += "Looks like we used up our quota for this hour because runtime_left_secs <= 0.  Good job!  No need to reschedule.\n"
                unschedule(turnFanOnForQuota)
            } else {
                debug_string += "Rescheduling to run in ${run_in_secs} seconds to make sure we meet our quota...\n"
                state.time_left_in_quota = runtime_left_secs
                runIn(run_in_secs, turnFanOnForQuota)
            }
        }
    } catch(e) {
        debug_string += "DARN.  Unhandled exception:\n${e}\n"
    }
    
    DEBUG(debug_string)
}

//------------------------------------------------------------------------------

private def secToMinSecString(total_seconds) {
    Integer minutes = total_seconds / 60
    Integer seconds = total_seconds - (minutes * 60)
    return "${minutes} min, ${seconds} secs"
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

def oldSchoolDump() {
    String  debug_string = ""
    Integer total_secs   = 0
    Integer total_mins   = 0
    
    try {
        Integer counter      = 0
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
        
        //debug_string += "-----------------------------------\n"
        debug_string += "---- Old School Stuff - 1 hour ----\n"
        //debug_string += "-----------------------------------\n"
        //debug_string += "  current time = ${cur}\n"
        debug_string += "  current time = ${formatted_current_time}\n"
        //debug_string += "  last hour    = ${hour_ago}\n"
        debug_string += "  last hour    = ${formatted_last_hour}\n"

        for(zzz in fanSwitch.eventsSince(hour_ago, [max: 1000]).reverse()) {
            if("${zzz.source}" == "DEVICE") {
                if(zzz.value == "on" || zzz.value == "off") {
                    counter += 1
                    debug_string += "   EVENT: ${counter}\n"
                    //debug_string += "     date            = ${zzz.date}\n"
                    debug_string += "     date            = ${df.format(zzz.date)}\n"
                    //debug_string += "     name            = ${zzz.name}\n"
                    //debug_string += "     device          = ${zzz.device.displayName}\n"
                    //debug_string += "     description     = ${zzz.description}\n"
                    //debug_string += "     descriptionText = ${zzz.descriptionText}\n"
                    //debug_string += "     state_change    = ${zzz.isStateChange()}\n"
                    //debug_string += "     physical        = ${zzz.isPhysical()}\n"
                    debug_string += "     value           = ${zzz.value}\n"
                    debug_string += "     last value      = ${last_event_value}\n"
                    //debug_string += "     source          = ${zzz.source}\n"

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
        }
       
        total_mins = total_secs / 60
        total_secs = total_secs - (total_mins * 60)
   
        debug_string += "  Total fan runtime last hour: ${total_mins} mins and ${total_secs} secs\n"
        
        DEBUG(debug_string)
       
        debug_string = "---- States in last hour\n"
        counter = 0
        for(zzz in fanSwitch.statesSince("switch", hour_ago, [max: 1000]).reverse()) {
            counter += 1
            debug_string += "   STATE: ${counter}\n"
            //debug_string += "     date            = ${zzz.date}\n"
            debug_string += "     date            = ${df.format(zzz.date)}\n"
            debug_string += "     value           = ${zzz.value}\n"
        }
        
        //debug_string += "---------------------------------------\n"
        //debug_string += "---- End Old School Stuff - 1 hour ----\n"
        //debug_string += "---------------------------------------\n"
        
    } catch(e) {
        debug_string += "DARN.  Unhandled exception in oldSchoolStuff:\n${e}\n"
    }
    
    DEBUG(debug_string)
    DEBUG("  Total fan runtime last hour: ${total_mins} mins and ${total_secs} secs\n")
}

//------------------------------------------------------------------------------

def oldSchoolDump24() {
    String  debug_string     = ""
    Integer total_total_secs = 0
    Integer total_secs       = 0
    Integer total_mins       = 0
    Integer counter          = 0
    
    debug_string += "\n"
    debug_string += "-------------------------------------\n"
    debug_string += "---- Old School Stuff - 24 hours ----\n"
    debug_string += "-------------------------------------\n"
        
    try {
        def     cur          = new Date()
        def     day_ago      = new Date()
        use(TimeCategory) {
            day_ago = day_ago - 24.hour
        }
        def last_event       = day_ago
        def last_event_value = "on" // 

        def df = new java.text.SimpleDateFormat("EEE MMM dd 'at' hh:mm:ss a")
        df.setTimeZone(location.timeZone)
        
        //debug_string += "  current time = ${cur}\n"
        debug_string += "  current time = ${df.format(cur)}\n"
        //debug_string += "  last hour    = ${day_ago}\n"
        debug_string += "  last day     = ${df.format(day_ago)}\n"

        for(zzz in fanSwitch.statesSince("switch", day_ago, [max: 1000]).reverse()) {
            counter += 1
           
            //debug_string += "   STATE: ${counter}\n"
            //debug_string += "     date            = ${zzz.date}\n"
            //debug_string += "     date            = ${df.format(zzz.date)}\n"
            //debug_string += "     name            = ${zzz.name}\n"
            //debug_string += "     device          = ${zzz.device.displayName}\n"
            //debug_string += "     description     = ${zzz.description}\n"
            //debug_string += "     descriptionText = ${zzz.descriptionText}\n"
            //debug_string += "     state_change    = ${zzz.isStateChange()}\n"
            //debug_string += "     physical        = ${zzz.isPhysical()}\n"
            //debug_string += "     value           = ${zzz.value}\n"
            //debug_string += "     last value      = ${last_event_value}\n"
            //debug_string += "     source          = ${zzz.source}\n"
            
            if(zzz.value == "off") {
                if(last_event_value == zzz.value) {
                    null
                    //debug_string += "     Last event was off so not counting...\n"
                } else {
                    def seconds_since_last_mark = (zzz.date.getTime() - last_event.getTime()) / 1000
                    total_total_secs += seconds_since_last_mark
                    //debug_string += "     seconds since last = ${seconds_since_last_mark}\n"
                    //debug_string += "     Total secs         = ${total_total_secs}\n"
                }
            }

            last_event       = zzz.date
            last_event_value = zzz.value
        }
        
        total_mins = total_total_secs / 60
        total_secs = total_total_secs - (total_mins * 60)
   
    } catch(e) {
        debug_string += "DARN.  Unhandled exception in oldSchool24:\n${e}\n"
    }
    
    def average = total_total_secs / 60.0 / 24.0
    
    debug_string += "Total fan runtime last 24 hours: ${counter} events for total of ${total_mins} mins and ${total_secs} secs\n"
    debug_string += "  --> average is ${average}\n"
    
    DEBUG(debug_string)
}

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
