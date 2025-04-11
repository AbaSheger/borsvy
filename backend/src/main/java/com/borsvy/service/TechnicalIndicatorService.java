package com.borsvy.service;

import com.borsvy.model.StockPrice;
import org.springframework.stereotype.Service;
import org.ta4j.core.*;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for calculating technical indicators using TA4J library
 */
@Service
public class TechnicalIndicatorService {

    /**
     * Converts a list of StockPrice objects to a TA4J BarSeries
         /**
     * Converts a list of StockPrice objects to a TA4J BarSeries
     */
    public BarSeries convertToBarSeries(List<StockPrice> priceData, String symbol) {
        BarSeries series = new BaseBarSeries(symbol);
        
        for (StockPrice price : priceData) {
            ZonedDateTime dateTime = price.getTimestamp().atZone(ZoneId.systemDefault());
            
            // Create a new bar with available data (use price for all OHLC since we only have close prices)
            Bar bar = BaseBar.builder()
                .timePeriod(Duration.ofMinutes(1))
                .endTime(dateTime)
                .openPrice(DecimalNum.valueOf(price.getPrice()))
                .highPrice(DecimalNum.valueOf(price.getPrice()))
                .lowPrice(DecimalNum.valueOf(price.getPrice()))
                .closePrice(DecimalNum.valueOf(price.getPrice()))
                .volume(DecimalNum.valueOf(price.getVolume()))
                .build();
            
            series.addBar(bar);
        }
        
        return series;
    }
      /**
     * Calculates RSI indicator
     */
    public double calculateRSI(BarSeries series, int period) {
        if (series.getBarCount() < period) {
            return 50.0; // Default value if not enough data
        }
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, period);
        
        return rsi.getValue(series.getEndIndex()).doubleValue();
    }
    
    /**
     * Calculates Simple Moving Average
     */
    public double calculateSMA(BarSeries series, int period) {
        if (series.getBarCount() < period) {
            return series.getBar(series.getEndIndex()).getClosePrice().doubleValue();
        }
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, period);
        
        return sma.getValue(series.getEndIndex()).doubleValue();
    }
      /**
     * Generates a comprehensive technical analysis
     */
    public Map<String, Object> generateTechnicalAnalysis(List<StockPrice> priceData, String symbol) {
        Map<String, Object> analysis = new HashMap<>();
        
        // Exit early if no price data
        if (priceData == null || priceData.isEmpty()) {
            analysis.put("error", "No price data available");
            return analysis;
        }
        
        try {
            // Convert price history to BarSeries
            BarSeries series = convertToBarSeries(priceData, symbol);
            
            // Calculate indicators
            double rsi = calculateRSI(series, 14);
            double sma20 = calculateSMA(series, 20);
            double sma50 = calculateSMA(series, 50);
            double currentPrice = series.getBar(series.getEndIndex()).getClosePrice().doubleValue();
            
            // Determine trend
            String trend;
            if (currentPrice > sma20 && sma20 > sma50) {
                trend = "bullish";
            } else if (currentPrice < sma20 && sma20 < sma50) {
                trend = "bearish";
            } else {
                trend = "neutral";
            }
            
            // Generate signals
            Map<String, String> signals = new HashMap<>();
            
            // RSI signals
            if (rsi > 70) {
                signals.put("RSI", "Overbought (RSI: " + String.format("%.2f", rsi) + ")");
            } else if (rsi < 30) {
                signals.put("RSI", "Oversold (RSI: " + String.format("%.2f", rsi) + ")");
            } else {
                signals.put("RSI", "Neutral (RSI: " + String.format("%.2f", rsi) + ")");
            }
            
            // Moving Average signals
            if (currentPrice > sma20) {
                signals.put("SMA20", "Price above 20-day SMA (Bullish)");
            } else {
                signals.put("SMA20", "Price below 20-day SMA (Bearish)");
            }
            
            if (currentPrice > sma50) {
                signals.put("SMA50", "Price above 50-day SMA (Bullish)");
            } else {
                signals.put("SMA50", "Price below 50-day SMA (Bearish)");
            }
            
            // Store results
            analysis.put("rsi", rsi);
            analysis.put("sma20", sma20);
            analysis.put("sma50", sma50);
            analysis.put("currentPrice", currentPrice);
            analysis.put("trend", trend);
            analysis.put("signals", signals);
            
        } catch (Exception e) {
            analysis.put("error", "Failed to generate technical analysis: " + e.getMessage());
        }
        
        return analysis;
    }
}
