package com.borsvy.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.Data;

@Data
public class NewsArticle {
    private String title;
    private String url;
    private String publishedDate;
    private String thumbnail;
    private String source;
    private String summary;

    public NewsArticle() {
    }

    public NewsArticle(String title, String summary, String url, String thumbnail) {
        this.title = title;
        this.summary = summary;
        this.url = url;
        this.thumbnail = thumbnail;
        this.source = "Yahoo Finance";
        this.publishedDate = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
    }

    // Getters and setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getPublishedDate() {
        return publishedDate;
    }

    public void setPublishedDate(String publishedDate) {
        this.publishedDate = publishedDate;
    }
} 