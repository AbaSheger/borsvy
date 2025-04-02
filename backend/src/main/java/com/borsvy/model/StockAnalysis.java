package com.borsvy.model;

import lombok.Data;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "stock_analysis")
public class StockAnalysis {
    @Id
    private String symbol;
    
    @Column(columnDefinition = "TEXT")
    private String aiAnalysis;
    
    @Column(columnDefinition = "TEXT")
    private String technical;
    
    @Column(columnDefinition = "TEXT")
    private String fundamental;
    
    @Column(columnDefinition = "TEXT")
    private String sentiment;
    
    @Column(columnDefinition = "TEXT")
    private String recommendation;
    
    @Column(columnDefinition = "TEXT")
    private String newsSentiment;
    
    private LocalDateTime timestamp;

    public StockAnalysis(String symbol, String technical, String fundamental, String sentiment, String recommendation) {
        this.symbol = symbol;
        this.technical = technical;
        this.fundamental = fundamental;
        this.sentiment = sentiment;
        this.recommendation = recommendation;
        this.timestamp = LocalDateTime.now();
    }

    public StockAnalysis() {
        this.timestamp = LocalDateTime.now();
    }
}