package com.phoebe.dto;

public class NoteResponse {

    private Long id;
    private String status;

    public NoteResponse() {
    }

    public NoteResponse(Long id, String status) {
        this.id = id;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public static NoteResponse stored(Long id) {
        return new NoteResponse(id, "stored");
    }
}
