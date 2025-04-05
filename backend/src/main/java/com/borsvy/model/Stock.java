package com.borsvy.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Stock {
    @Id
    private String symbol;
    
    private String name;
    private String industry;
    private double price;
    private double change;
    private double changePercent;
    private double high;
    private double low;
    private double open;
    private long volume;
    private double marketCap;
    private double peRatio;
    private double beta;
    private double high52Week;
    private double low52Week;
    private double dividendYield;
    private double earningsPerShare;
    private double sharesOutstanding;
    private double dividend;
    private LocalDateTime lastUpdated;
    
    // Additional fields for technical analysis
    private double rsi;
    private double macd;
    private double sma20;
    private double sma50;
}