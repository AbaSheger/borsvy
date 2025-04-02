package com.borsvy.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CompanyProfile2 {
    private String name;
    
    @JsonProperty("finnhubIndustry")
    private String finnhubIndustry;
    
    @JsonProperty("marketCapitalization")
    private double marketCapitalization;
    
    @JsonProperty("beta")
    private double beta;
    
    @JsonProperty("pe")
    private double pe;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFinnhubIndustry() {
        return finnhubIndustry;
    }

    public void setFinnhubIndustry(String finnhubIndustry) {
        this.finnhubIndustry = finnhubIndustry;
    }

    public double getMarketCapitalization() {
        return marketCapitalization;
    }

    public void setMarketCapitalization(double marketCapitalization) {
        this.marketCapitalization = marketCapitalization;
    }

    public double getBeta() {
        return beta;
    }

    public void setBeta(double beta) {
        this.beta = beta;
    }

    public double getPe() {
        return pe;
    }

    public void setPe(double pe) {
        this.pe = pe;
    }
} 