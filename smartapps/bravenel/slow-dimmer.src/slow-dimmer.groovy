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
        log.debug "initialize: minutes=$minutes"
    	subscribe(trigger, "switch.on", triggerHandler)
    }
   
def triggerHandler(evt) {
    log.debug "triggerHandler"
   
    // unschedule any current running instances of this smartapp
    unschedule()
   
    if(dimmers[0].currentSwitch != "on")
    {
    	log.debug "triggerHandler   switch is off!?!?  canceling"
        return
    }
    else if(minutes <= 0)
    {
    	log.debug "triggerHandler    minutes is [$minutes] which is an invalid value!"
        return
    }
   
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

    log.debug "triggerHandler   currentLevel     = $currentLevel"
    log.debug "triggerHandler   minutes to take  = $minutes"
    log.debug "triggerHandler   dimStep          = $state.dimStep"
    log.debug "triggerHandler   iterations       = $iterations"
    log.debug "triggerHandler   delay_seconds    = $state.delay_seconds"
    log.debug "triggerHandler   actual_total_min = $actual_total_min"
    
    //runIn(state.delay_seconds, dimStep)
    dimStep()
}

def dimStep() {
    log.debug "dimStep"
    
    def currentLevel = dimmers[0].currentLevel
    log.debug "dimStep: currentLevel = $currentLevel"
    log.debug "dimStep: lastLevel    = $state.lastLevel"
    
    if(currentLevel > state.lastLevel)
    {
        log.debug "dimStep: currentLevel [currentLevel] is greater than last level [$state.lastLevel], cancel slow dimmer"
        return
    }
   
    def newLevel = currentLevel - state.dimStep
    log.debug "dimStep: newLevel     = $newLevel"
    
    if(newLevel < 1)
    {
    	log.debug "dimStep: next level less than 0 so turn it off..."
        dimmers.off()
        return
    }
    
    dimmers.setLevel(newLevel)
    state.lastLevel = newLevel
    
    runIn(state.delay_seconds, dimStep)
}