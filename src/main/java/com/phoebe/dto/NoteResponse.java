package com.phoebe.dto;

public class NoteResponse {

    private String id;
    private String status;

    public NoteResponse() {
    }

    public NoteResponse(String id, String status) {
        this.id = id;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public static NoteResponse stored(String id) {
        return new NoteResponse(id, "stored");
    }
}
