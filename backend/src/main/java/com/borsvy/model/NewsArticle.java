package com.borsvy.model;

import lombok.Data;

@Data
public class NewsArticle {
    private String title;
    private String url;
    private String publishedDate;
    private String thumbnail;
    private String source;
    private String summary;
} 