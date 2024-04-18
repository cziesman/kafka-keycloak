package com.redhat.kafka.client.service;

import java.time.ZonedDateTime;

public class Message {

    private ZonedDateTime timestamp;

    private String text;

    public ZonedDateTime getTimestamp() {

        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {

        this.timestamp = timestamp;
    }

    public String getText() {

        return text;
    }

    public void setText(String text) {

        this.text = text;
    }

}
