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
 
// TODO: This is a massively hacked up version of the smartapp provided by
//       smartthings.  Much of this is my attempt at learning this wacky
//       groovy language...  Maybe someday I'll clean it up and make it proper

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

    section( "Enabley" )
    {
        input "enabled", "bool",  title: "Enabled", defaultValue: true, required: true
    }
	section("Monitor these doors or windows") {
		input "contact_sensors", "capability.contactSensor", multiple: true, required: true
	}
	section("And notify me if it's open for more than this many minutes") {
		input "openThresholdMin", "number", title: "Minutes", defaultValue: 10, required: true
	}
    section("Delay between notifications") {
        input "warnPeriodMin",     "number", title: "Minutes", defaultValue: 10, required: true
    }
    section( "Notifications" )
    {
        input "sendPushMessage", "bool",  title: "Send a push notification?", required: true, defaultValue: false
        input "phone",           "phone", title: "Send a Text Message?",      required: false
    }
}

def installed() {
	log.debug "installed()"
    updated()
}

def updated() {
	log.debug "updated()"
    unschedule()
	unsubscribe()
    resetState()
	subscribe()
    checkDoorOpenTooLong()
}

def resetState() {
    log.debug "resetState()"
    state.clear()
    state.openTime          = [:]
    state.lastWarnTime      = [:]
    state.warnPeriodMinutes = [:]
    for (contact in settings.contact_sensors) {
        log.debug "    --> Contact $contact -> $contact.contactState.value"
        state.warnPeriodMinutes[contact.id] = warnPeriodMin
        if (contact.contactState && contact.contactState.value == "open" ) {
            state.openTime[contact.id] = contact.contactState.rawDateCreated.time
        }
    }
}

def subscribe() {
    log.debug "subscribe()"
	subscribe(settings.contact_sensors, "contact.open",   doorOpen)
	subscribe(settings.contact_sensors, "contact.closed", doorClosed)
}

def doorOpen(evt)
{
	log.debug "doorOpen($evt.device -> $evt.name: $evt.value)"
    state.openTime[evt.deviceId] = now()
    checkDoorOpenTooLong()
}

def doorClosed(evt)
{
	log.debug "doorClosed($evt.device -> $evt.name: $evt.value)"
    
    if (state.lastWarnTime[evt.deviceId])
        log.debug "We have actually issued a warning for this one..."
    else
        log.debug "We never warned about this one..."
    
    if (state.lastWarnTime[evt.deviceId])
        sendClosedMessage(evt.device)
    state.openTime.remove(evt.deviceId)
    state.lastWarnTime.remove(evt.deviceId)
    state.warnPeriodMinutes[evt.deviceId] = warnPeriodMin
    
    if (state.lastWarnTime[evt.deviceId])
        log.debug "IT STILL THERE!"
    else
        log.debug "its not there which is good"
        
    checkDoorOpenTooLong()
}

def checkDoorOpenTooLong()
{
    log.debug "checkDoorOpenTooLong()"

    def nextRunMinList = []
   
    log.debug "-------------------------------------"
    
    // loop over all the open contact sensors
    def open_sensors = settings.contact_sensors.findAll { it?.contactState && it?.contactState.value == "open" }
    for (contact in open_sensors)
    {
        log.debug "    OPEN CONTACT: ${contact.displayName}"
        
        def openTimeMin = (now() - state.openTime[contact.id]) / 60000
        def nextRunMin  = state.warnPeriodMinutes[contact.id]
        
        log.debug "        --> Opened ${openTimeMin} minutes ago"
       
        if (state.lastWarnTime[contact.id])
        {
            // we've issued a warning already, is it time to issue another
            // or check to see when we need to run again
            def timeSinceLastWarnMin = (now() - state.lastWarnTime[contact.id]) / 60000
            log.debug "        --> this contact had already issued a warning ${timeSinceLastWarnMin} minutes ago"
            if(timeSinceLastWarnMin > state.warnPeriodMinutes[contact.id])
            {
                log.debug "       --> send notification... again!"
                sendOpenMessage(contact)
                // bump the warn period so we don't annoy ourselves
                state.warnPeriodMinutes[contact.id] = state.warnPeriodMinutes[contact.id] * 2
                log.debug "   warn period for ${contact.displayName} updated to ${state.warnPeriodMinutes[contact.id]}"
                nextRunMin = state.warnPeriodMinutes[contact.id]
            }
            else
            {
                nextRunMin = state.warnPeriodMinutes[contact.id] - timeSinceLastWarnMin
                log.debug "       --> Not time yet for warning."
            }
        }
        else if(openTimeMin > openThresholdMin)
        {
            // no warning issued yet, so lets issue the first yay
            log.debug "       --> send notification!"
            sendOpenMessage(contact)
        }
        else
        {
            // no warnings issued yet.  We must be here
            // because another contact was opened before it...
            nextRunMin = (openThresholdMin - openTimeMin)
        }

        log.debug "       --> next run in ${nextRunMin} minutes"
        nextRunMinList << nextRunMin
        
    } // END loop over each contact sensor
   
    if (nextRunMinList)
    {
        def nextRunMin = Math.ceil(nextRunMinList.min())
        log.debug "NEXT RUN: ${nextRunMin} minutes"
        runIn( (nextRunMin * 60) + 10, checkDoorOpenTooLong, [overwrite: true])
    }
    else
    {
        log.debug "NOTHING LEFT TO CHECK..."
        unschedule()
    }
    
    log.debug "-------------------------------------"
}

void sendClosedMessage(contact)
{
    log.debug "sendClosedMessage(${contact.displayName})"
   
    // def openTimeMin = (now() - state.openTime[contact.id]) / 60000
    // def msg         = "${contact.displayName} has been closed after ${openTimeMin} minutes.  Geez!"
    // sendMessage(msg)
    
    for (evt in contact.events(max: 5))
    {
        log.debug "EVENT: ${evt.device} -> ${evt.name}: ${evt.value} ${evt.date}"
        if (evt.value == "open")
        {
            def openTimeMin = (now() - evt.date.time) / 60000
	        def msg         = "${contact.displayName} closed after ${Math.round(openTimeMin)} minutes.  Geez!"
            sendMessage(msg)
            return
        }
    }

    log.debug "sendClosedMessage called for ${contact.displayName}, but couldn't figure out when last opened..."
}

void sendOpenMessage(contact)
{
    log.debug "sendMessage(${contact.displayName})"
    state.lastWarnTime[contact.id] = now()
    def openTimeMin = (now() - state.openTime[contact.id]) / 60000
	def msg         = "${contact.displayName} left open for ${Math.round(openTimeMin)} minutes."
    sendMessage(msg)
}

void sendMessage(msg)
{
	log.info msg
    
    if( state.enabled == false )
    {
        log.warn "App is disabled.  Not sending notifications..."
    }
    else if (location.contactBookEnabled)
    {
        sendNotificationToContacts(msg, recipients)
    }
    else
    {
        if (settings.phone)
        {
            log.debug "Here I would send a message to: ${settings.phone}"
            sendSms phone, msg
        }
        
        if (settings.sendPushMessage)
        {
            log.debug "Here I would send a push notification"
            sendPush msg
        }
    }
}