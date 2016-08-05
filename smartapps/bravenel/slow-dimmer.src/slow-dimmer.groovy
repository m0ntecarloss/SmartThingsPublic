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
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

def initialize() {
    DEBUG("initialize: minutes=$minutes")
    subscribe(trigger, "switch.on",     triggerHandler)
    subscribe(motions, "motion.active", motionHandler)
    state.slow_dim_in_progress = false
    state.start_time = now()
}
    
def motionHandler(evt)
{
    log.debug("-------------------------------------")
    
    log.debug("motionHandler: slow_dim_in_progress=${state.slow_dim_in_progress}")
    if(state.slow_dim_in_progress)
    {
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
    
def triggerHandler(evt) {

    log.debug("-------------------------------------")
    
    DEBUG("triggerHandler: Slow dim action started...")
   
    // kick off the dimmer...
    state.slow_dim_in_progress = true
    state.start_time           = now()
    state.last_motion          = now()
    
    // (re)calculate amount to dim by and when to dim...
    setupDimStep()
   
    //dimStep()
}

def setupDimStep()
{
    log.debug("-------------------------------------")
    
    // unschedule any current running instances of this smartapp
    // TODO: we can get rid of this...
    unschedule()
 
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

    log.debug "setupDimStep:   currentLevel     = $currentLevel"
    log.debug "setupDimStep:   minutes to take  = $minutes"
    log.debug "setupDimStep:   dimStep          = $state.dimStep"
    log.debug "setupDimStep:   iterations       = $iterations"
    log.debug "setupDimStep:   delay_seconds    = $state.delay_seconds"
    log.debug "setupDimStep:   actual_total_min = $actual_total_min"
    
    // re-schedule again in delay_seconds
    log.debug "setupDimStep:  rescheduling in ${state.delay_seconds} seconds"
    runIn(state.delay_seconds, dimStep)
}

def dimStep()
{
    def total_time_min         = (now() - state.start_time)  / 1000.0 / 60.0
    def time_since_last_motion = (now() - state.last_motion) / 1000.0 / 60.0
   
    log.debug("-------------------------------------")
    
    log.debug("dimStep: state.slow_dim_in_progress=${state.slow_dim_in_progress}   total_time_min=${total_time_min} minutes   since_last_motion=${time_since_last_motion} minutes.")
    
    if(!state.slow_dim_in_progress)
    {
        DEBUG("dimStep: slow_dim_in_progress=${state.slow_dim_in_progress}.  I guess I'm done...\n" + 
              "dimStep: after ${total_time_min} it looks like I'm supposed to stop...\n" +
              "dimStep: Quit early after ${total_time_min}...  Time since last motion was ${time_since_last_motion} minutes.")
        return
    }
    
    def currentLevel = dimmers[0].currentLevel
    log.debug "dimStep: currentLevel = $currentLevel"
    log.debug "dimStep: lastLevel    = $state.lastLevel"
  
    // TODO: use setLevel event to cancel...
/*
    if(currentLevel > state.lastLevel)
    {
        log.debug "dimStep: currentLevel [currentLevel] is greater than last level [$state.lastLevel], cancel slow dimmer"
        sendNotificationEvent("Lights turned up, cancelling slow dimmage")
        return
    }
*/
   
    def newLevel = currentLevel - state.dimStep
    log.debug "dimStep: newLevel     = $newLevel"
    
    if(newLevel < 1)
    {
        dimmers.off()
        state.slow_dim_in_progress = false
        DEBUG("\n" +
              "dimStep: next level less than 1 so turn it off...\n" +
              "dimStep:     Time to complete:       ${total_time_min} min\n" +
              "dimStep:     Time since last motion: ${time_since_last_motion} min\n")
    }
    else
    {
        // change dimmer value here
        dimmers.setLevel(newLevel)
        state.lastLevel = newLevel
 
        // re-schedule again in delay_seconds
        log.debug "dimStep:  rescheduling in ${state.delay_seconds} seconds"
        runIn(state.delay_seconds, dimStep)
    }
}

private def DEBUG(txt)
{
    //log.debug ${app.label}
    log.debug(txt)
    sendNotificationEvent(txt)
}