package com.borsvy.model;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockDetails {
    private String symbol;
    private String name;
    private String industry;
    private Double marketCap;
    private Double price;
    private Double change; // Absolute change
    private Double changePercent; // Percentage change
    private Double high;
    private Double low;
    private Double open;
    private Double previousClose;
    private Double peRatio;
    private Double beta;
    private Long volume;
} 