//IntentType.java
package com.yakshith.voiceassistant.assistant;

public enum IntentType {
    // Existing
    CALL_CONTACT,
    OPEN_APP,
    SEARCH_WEB,
    GET_TIME,
    GET_DATE,
    GREETING,

    // ✅ New intents
    SET_ALARM,
    SET_TIMER,
    WHATSAPP_MSG,
    PLAY_MUSIC,
    GET_BATTERY,
    GET_WEATHER,

    UNKNOWN
}