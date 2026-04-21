package com.tiaozhanbei.dto;

public class ChatResponse {
    private String reply;
    private Integer code;
    private String message;

    public ChatResponse() {}

    public ChatResponse(Integer code, String message, String reply) {
        this.code = code;
        this.message = message;
        this.reply = reply;
    }

    public static ChatResponse success(String reply) {
        return new ChatResponse(200, "success", reply);
    }

    public static ChatResponse error(String message) {
        return new ChatResponse(500, message, null);
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}