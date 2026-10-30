package com.billing.invoicehub.dto;

import java.util.ArrayList;
import java.util.List;

public class ReleaseNoteDto {
    private String version;
    private String date;
    private String audience = "ALL";
    private String titleEn;
    private String titleId;
    private List<String> notesEn = new ArrayList<>();
    private List<String> notesId = new ArrayList<>();

    public ReleaseNoteDto() {
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public void setTitleEn(String titleEn) {
        this.titleEn = titleEn;
    }

    public String getTitleId() {
        return titleId;
    }

    public void setTitleId(String titleId) {
        this.titleId = titleId;
    }

    public List<String> getNotesEn() {
        return notesEn;
    }

    public void setNotesEn(List<String> notesEn) {
        this.notesEn = notesEn;
    }

    public List<String> getNotesId() {
        return notesId;
    }

    public void setNotesId(List<String> notesId) {
        this.notesId = notesId;
    }
}
