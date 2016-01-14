/**
 *  Copyright 2015 SmartThings
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
 *  Left It Open
 *
 *  Author: SmartThings
 *  Date: 2013-05-09
 */
 
import groovy.time.TimeDuration
import groovy.time.TimeCategory

definition(
    name: "Left It Open",
    namespace: "smartthings",
    author: "SmartThings",
    description: "Notifies you when you have left one or more doors or windows open longer that a specified amount of time.",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/ModeMagic/bon-voyage.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/ModeMagic/bon-voyage%402x.png"
)

preferences {

	section("Monitor these doors or windows") {
		input "contact_sensors", "capability.contactSensor", multiple: true
	}
	section("And notify me if it's open for more than this many minutes") {
		input "openThreshold", "number", title: "Minutes", required: true, defaultValue: 10
	}
    section("Delay between notifications") {
        input "warnPeriodMin",     "number", title: "Minutes", required: true, defaultValue: 10
    }
    /*
    section( "Notifications" )
    {
        input "sendPushMessage", "bool",  title: "Send a push notification?", required: false, defaultValue: false
        input "phone",           "phone", title: "Send a Text Message?",      required: false
    }
    */
}

def installed() {
	log.trace "installed()"
    resetState()
	subscribe()
}

def updated() {
	log.trace "updated()"
    unschedule()
	unsubscribe()
    resetState()
	subscribe()
}

def resetState() {
    log.debug "resetState()"
   
    state.clear()
    state.openTime       = [:]
    state.warnTime       = [:]
    state.warnAt         = [:]
    
    // NOTE: may need to use atomic state here?
    state.checkScheduled = false
    
    for (xxx in contact_sensors)
    {
        log.debug "${xxx.name}: ${xxx.contact} ${xxx.id} ${xxx.displayName}"
        //state.openTime[xxx.id] = 0
    }
    
    // log each capability supported by the "mySwitch" device, along
    // with all its supported attributes
    // contact_sensors.each { xxx ->
        // log.debug "${xxx}: ${xxx.name} -> " + xxx.currentState("contact")
        //log.debug "${xxx}: ${xxx.name} -> " + xxx.contactState.value
    // }
    
    log.debug state
}

def subscribe() {
    log.debug "subscribe()"
	subscribe(contact_sensors, "contact.open",   doorOpen)
	subscribe(contact_sensors, "contact.closed", doorClosed)
}

def doorOpen(evt)
{
	log.trace "doorOpen($evt.device -> $evt.name: $evt.value)"
 
    state.openTime[evt.deviceId] = now()
    state.warnAt[evt.deviceId]   = now() + (settings.openThreshold * 60)
    
    // first time a door is opened, the delay should match the
    // threshold, if we have already scheduled the runIn timer
    // then don't bother and then determine the runIn delay
    // in that routine based on the open doors
   
    if(state.checkScheduled)
    {
        log.debug "check has already been scheduled..."
    }
    else
    {
        def delay = (openThreshold != null && openThreshold != "") ? openThreshold * 60 : 600
        runIn(delay, doorOpenTooLong, [overwrite: false])
        state.checkScheduled = true
        log.debug "Check scheduled for ${delay} seconds from now"
    }
}

def doorClosed(evt)
{
	log.trace "doorClosed($evt.device -> $evt.name: $evt.value)"
    sendClosedMessage(evt.device)
    state.openTime.remove(evt.deviceId)
    state.warnTime.remove(evt.deviceId)
    state.warnAt.remove(evt.deviceId)
}

def doorOpenTooLong() {

    def nextRunDelaySec        = null
   
    log.debug "-------------------------------------"
    
    // loop over all the open contact sensors
    def open_sensors = contact_sensors.findAll { it?.contactState.value == "open" }
    for (contact in open_sensors)
    {
        log.debug "    OPEN CONTACT: ${contact.displayName}"
        
        def diff_minutes      = (now() - state.openTime[contact.id]) / 60000
        def temp_next_run     = 0

        log.debug "        --> Opened ${diff_minutes} minutes ago"
       
        if (state.warnTime[contact.id])
        {
            // we've issued a warning already, is it time to issue another
            // or check to see when we need to run again

            def warn_diff_minutes = (now() - state.warnTime[contact.id]) / 60000
            log.debug "        --> this contact had already issued a warning at: ${warn_diff_minutes} minutes ago"

            if(warn_diff_minutes > warnPeriodMin)
            {
                state.warnTime[contact.id] = now()
                temp_next_run              = warnPeriodMin * 60
                log.debug "       --> send notification... again!"
            }
            else
            {
                temp_next_run = (warnPeriodMin - warn_diff_minutes) * 60
                log.debug "       --> no warning yet...  Run again in ${temp_next_run} seconds"
            }
        }
        else if(diff_minutes > openThreshold)
        {
            // no warning issued yet, so lets issue the first yay

            state.warnTime[contact.id] = now()
            log.debug "       --> send notification!"
            sendMessage(contact)

            // we issued a warning, so our next run time is based on the warn period
            temp_next_run              = warnPeriodMin * 60
        }
        else
        {
            // no warnings issued yet...
            temp_next_run = (openThreshold - diff_minutes) * 60
        }

        // If we need to run again, lets see if its quicker than
        // the others so we only schedule once...
        if(temp_next_run > 0)
        {
            log.debug "        --> next run in ${temp_next_run} seconds"
            if( (nextRunDelaySec == null) || (temp_next_run < nextRunDelaySec) )
            {
                nextRunDelaySec = temp_next_run
            }
        }
            
    } // END loop over each contact sensor
    
    log.debug "NEXT RUN: ${nextRunDelaySec} seconds"
    log.debug "-------------------------------------"
    
    if( (nextRunDelaySec != null) && (nextRunDelaySec > 0) )
    {
        state.checkScheduled = true
        runIn(nextRunDelaySec, doorOpenTooLong, [overwrite: false])
    }
    else
    {
        state.checkScheduled = false
    }
    
   
        /*
        // does this sensor have a last opened value in the table
        def openTime = state.openTime[contact.id]
        if (openTime != null)
        {
            // check open/closed state, and reset counter and issue warning
            //     if currently closed (that would mean we missed the
            //     closed event)
            //
            // check if any contacts open too long and send out te
            //     message to user
            //
            // check if any contacts need a reminder issued
            //
            // use a single reschedule time variable and at end of routine
            //     reschedule the handler for the smallest time in future
            def diff_minutes = (now() - openTime) / 60000
            def diff_minutes2 = (now() - contact.contactState.rawDateCreated.time) / 60000
            log.debug "${contact.displayName} ${contact.contactState.value} for ${diff_minutes} minutes or ${diff_minutes2} minutes"
        }
        else
        {
            log.debug "${contact.displayName} ${contact.contactState.value} - was not in table!"
        }
        */
	/* def contactState = contact.currentState("contact")
    def freq = (warnPeriodMin != null && warnPeriodMin != "") ? warnPeriodMin * 60 : 600

	if (contactState.value == "open") {
		def elapsed = now() - contactState.rawDateCreated.time
		def threshold = ((openThreshold != null && openThreshold != "") ? openThreshold * 60000 : 60000) - 1000
		if (elapsed >= threshold) {
			log.debug "Contact has stayed open long enough since last check ($elapsed ms):  calling sendMessage()"
			sendMessage()
            runIn(freq, doorOpenTooLong, [overwrite: false])
		} else {
			log.debug "Contact has not stayed open long enough since last check ($elapsed ms):  doing nothing"
		}
	} else {
		log.warn "doorOpenTooLong() called but contact is closed:  doing nothing"
	} */
}

void sendClosedMessage(contact)
{
    log.debug "sendClosedMessage(${contact.displayName})"
   
    for (evt in contact.events(max: 5))
    {
        log.debug "EVENT: ${evt.device} -> ${evt.name}: ${evt.value} ${evt.date}"
        if (evt.value == "open")
        {
            def diff_minutes = (now() - evt.date.time) / 60000
            log.debug "${evt.device} closed after ${diff_minutes} minutes"
       
            def fuckyou = new Date()
            log.debug fuckyou
            log.debug fuckyou.time
        
            TimeDuration duration = fuckyou - evt.date
            log.debug duration
            
            return
        }
    }

    log.debug "sendClosedMessage called for ${contact.displayName}, but couldn't figure out when last opened..."
}

void sendMessage(contact)
{
    def diff_minutes = (now() - contact.contactState.rawDateCreated.time) / 60000
	def msg = "${contact.displayName} has been left open for ${diff_minutes} minutes."
    
	log.info msg
    
    if (location.contactBookEnabled) {
        sendNotificationToContacts(msg, recipients)
    }
    else {
   
        if (settings.phone) {
            log.debug "Here I would send a message to: ${settings.phone}"
            //sendSms phone, msg
        }
        
        if (settings.sendPushMessage) {
            log.debug "Here I would send a push notification"
            //sendPush msg
        }
        
    }
}