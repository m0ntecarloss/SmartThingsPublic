/**
 *  Slow Dimmer
 *
 *  Copyright 2015 Bruce Ravenel
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
    name: "Slow Dimmer",
    namespace: "bravenel",
    author: "Bruce Ravenel",
    description: "Slowly reduce dim level",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
    section("Select dimmers to slowly dim...") {
        input "dimmers", "capability.switchLevel", title: "Which?", required: true, multiple: true
    }

    section("Over how many minutes to dim...") {
        input "minutes", "number", title: "Minutes?", required: true, multiple: false
    }

    section("Restart countdown if motion on these sensors...") {
        input "motions",          "capability.motionSensor", title: "Motions",                         required: false, multiple: true
        input "dimLevelOnMotion", "number",                  title: "Dim Level to raise to on motion", required: false, multiple: false
    }

    //section("What is minimum dim value...") {
        //input "mindim", "number", title: "mindim?", required: true, multiple: false
    //}

    section("Select momentary button to launch...") {
        input "trigger", "capability.momentary", title: "Which?", required: true
    }
    
    section(hideable: True, "Debug") {
        input "debugEnabled", "bool", title: "Debug Enabled", required: True
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

def initialize() {
    DEBUG("initialize: xxx minutes=$minutes")
    subscribe(trigger, "switch.on",     triggerHandler)
    subscribe(motions, "motion.active", motionHandler)
    subscribe(dimmers, "switch.off",    dimmerOffHandler)
    state.slow_dim_in_progress = false
    state.start_time = now()
}
    
def motionHandler(evt) {
    
    DEBUG("-------------------------------------")
    DEBUG("motionHandler: slow_dim_in_progress=${state.slow_dim_in_progress}")
    DEBUG("motionHandler: current switch value = ${dimmers[0].currentSwitch}")
    
    if(state.slow_dim_in_progress) {
        if(dimmers[0].currentSwitch == "off") {
            DEBUG("motionHandler: Confused as fuck.  motion handler has been called with slow dim in progress but the switch is fucking off")
        } else {
            DEBUG("motionHandler: Saw motion.  Extending time...")
            state.last_motion = now()
            if(dimmers[0].currentLevel < settings.dimLevelOnMotion)
            {
                DEBUG("motionHandler: bumping current level to at least ${settings.dimLevelOnMotion}")
                dimmers.setLevel(settings.dimLevelOnMotion)
            }
        
            // (re)calculate amount to dim by and when to dim...
            setupDimStep()
        }
    }
}
    
def triggerHandler(evt) {

    DEBUG("-------------------------------------")
    DEBUG("triggerHandler: Slow dim action started...")
   
    // kick off the dimmer...
    if(dimmers[0].currentSwitch == "off") {
        DEBUG("triggerHandler: Hey cockbag, the switch is already off...  Not doing a damn thing")
    } else {
        state.slow_dim_in_progress = true
        state.start_time           = now()
        state.last_motion          = now()
    
        // (re)calculate amount to dim by and when to dim...
        setupDimStep()
    }
}

def dimmerOffHandler(evt) {
    DEBUG("-------------------------------------")
    DEBUG("dimmerOffHandler: dimmer was turned off.  Cancel slow dim")
    state.slow_dim_in_progress = false
    //trigger.off()
    unschedule(dimstep)
}

def setupDimStep()
{
    DEBUG("-------------------------------------")
    
    // unschedule any current running instances of this smartapp
    // TODO: we can get rid of this...
    unschedule(dimStep)
 
    // systematically determine dim step and delays between
    // dimmage...  This could certainly be done other ways
    // but this is easier to follow I think...
    
    def currentLevel = dimmers[0].currentLevel
    
    // calculate dim stepping value, but round up to nearest int
    state.dimStep = Math.ceil(currentLevel / minutes)

    // based on actual dimstep determine how many times
    // we need to actually do some dimmage
 	def iterations = Math.round(currentLevel / state.dimStep)
   
    // re-calculate minutes based on actual dimstep value
    // (and convert to seconds)
    state.delay_seconds = Math.ceil(60 * minutes / iterations)
  
    def actual_total_min = iterations * state.delay_seconds / 60

    // initialize last value
    state.lastLevel = currentLevel

    DEBUG("setupDimStep:   currentLevel     = $currentLevel")
    DEBUG("setupDimStep:   minutes to take  = $minutes")
    DEBUG("setupDimStep:   dimStep          = $state.dimStep")
    DEBUG("setupDimStep:   iterations       = $iterations")
    DEBUG("setupDimStep:   delay_seconds    = $state.delay_seconds")
    DEBUG("setupDimStep:   actual_total_min = $actual_total_min")
    
    // re-schedule again in delay_seconds
    DEBUG("setupDimStep:  rescheduling in ${state.delay_seconds} seconds")
    runIn(state.delay_seconds, dimStep)
}

def dimStep()
{
    def total_time_min         = (now() - state.start_time)  / 1000.0 / 60.0
    def time_since_last_motion = (now() - state.last_motion) / 1000.0 / 60.0
   
    DEBUG("-------------------------------------")
    
    DEBUG(String.format("dimStep: state.slow_dim_in_progress=%s   total_time_min=%.1f minutes  since_last_motion=%.1f minutes", state.slow_dim_in_progress,
                                                                                                                                total_time_min,
                                                                                                                                time_since_last_motion))
    
    if(!state.slow_dim_in_progress)
    {
        DEBUG("dimStep: slow_dim_in_progress=${state.slow_dim_in_progress}.  I guess I'm done...\n" + 
              "dimStep: after " + String.format("%.1f min", total_time_min) + " it looks like I'm supposed to stop...\n" +
              "dimStep: Quit early after " + String.format("%.1f min...", total_time_min) + "  Time since last motion was " + 
              String.format("%.1f minutes.", time_since_last_motion))
        return
    }
    
    def currentLevel = dimmers[0].currentLevel
    DEBUG("dimStep: currentLevel = $currentLevel")
    DEBUG("dimStep: lastLevel    = $state.lastLevel")
  
    // TODO: use setLevel event to cancel...
/*
    if(currentLevel > state.lastLevel)
    {
        state.slow_dim_in_progress = false
        trigger.off()
        DEBUG("dimStep: currentLevel [currentLevel] is greater than last level [$state.lastLevel], cancel slow dimmer")
        sendNotificationEvent("Lights turned up, cancelling slow dimmage")
        return
    }
*/
   
    def newLevel = currentLevel - state.dimStep
    DEBUG("dimStep: newLevel     = $newLevel")
    
    if(newLevel < 1)
    {
        dimmers.off()
        state.slow_dim_in_progress = false
        DEBUG("\n" +
              "dimStep: next level less than 1 so turn it off...\n" +
              "dimStep:     Time to complete:       " + String.format("%.1f min\n", total_time_min) +
              "dimStep:     Time since last motion: " + String.format("%.1f min\n", time_since_last_motion))
    }
    else
    {
        // change dimmer value here
        dimmers.setLevel(newLevel)
        state.lastLevel = newLevel
 
        // re-schedule again in delay_seconds
        DEBUG("dimStep:  rescheduling in ${state.delay_seconds} seconds")
        runIn(state.delay_seconds, dimStep)
    }
}

private def DEBUG(txt)
{
    //log.debug ${app.label}
    log.debug(txt)
    if( debugEnabled ) {
        sendNotificationEvent(txt)
    }
}