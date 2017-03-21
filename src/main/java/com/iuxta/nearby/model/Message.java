package com.iuxta.nearby.model;

import java.util.Date;

/**
 * Created by kerrk on 9/3/16.
 */
public class Message {

    private Date timeSent;

    private String content;

    private String senderId;

    public Message() {

    }

    public Date getTimeSent() {
        return timeSent;
    }

    public void setTimeSent(Date timeSent) {
        this.timeSent = timeSent;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }
}
