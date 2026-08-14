//AssistantCommand.java
package com.yakshith.voiceassistant.assistant;

public class AssistantCommand {

    public IntentType intent;
    public String entity;

    public AssistantCommand(IntentType intent, String entity) {
        this.intent = intent;
        this.entity = entity;
    }
}
