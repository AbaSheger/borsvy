package com.borsvy.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StockPrice {
    private String symbol;
    private double price; // close price
    private double open;
    private double high;
    private double low;
    private LocalDateTime timestamp;
    private double change;
    private double changePercent;
    private long volume;
} 