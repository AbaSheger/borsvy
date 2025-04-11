package com.borsvy.service;

import com.borsvy.model.StockPrice;
import org.springframework.stereotype.Service;
import org.ta4j.core.*;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;
import java.time.Duration;
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
    }    /**
     * Calculates RSI indicator
     */
    public double calculateRSI(BarSeries series, int period) {
        if (series.getBarCount() < period + 1) {
            // Log that we don't have enough data
            return calculateSimpleRSI(series); // Use simplified calculation instead of default
        }
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, period);
        
        return rsi.getValue(series.getEndIndex()).doubleValue();
    }
    
    /**
     * Simple RSI calculation when not enough historical data is available
     */
    private double calculateSimpleRSI(BarSeries series) {
        if (series.getBarCount() < 2) {
            return 50.0; // Truly not enough data
        }
        
        // Calculate a simple RSI based on the last price movement
        double currentClose = series.getBar(series.getEndIndex()).getClosePrice().doubleValue();
        double previousClose = series.getBar(series.getEndIndex() - 1).getClosePrice().doubleValue();
        
        double priceChange = currentClose - previousClose;
        
        // Simple RSI formula that gives higher values for positive changes
        if (priceChange > 0) {
            // Positive change - map to 50-100 range
            double percentChange = (priceChange / previousClose) * 100;
            return 50.0 + Math.min(percentChange * 5, 45.0); // Cap at 95
        } else {
            // Negative change - map to 0-50 range
            double percentChange = Math.abs(priceChange / previousClose) * 100;
            return 50.0 - Math.min(percentChange * 5, 45.0); // Cap at 5
        }
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
     * Calculates MACD indicator
     */
    public Map<String, Double> calculateMACD(BarSeries series) {
        Map<String, Double> macdData = new HashMap<>();
        
        if (series.getBarCount() < 26) { // MACD needs at least 26 bars
            macdData.put("macd", 0.0);
            macdData.put("signal", 0.0);
            macdData.put("histogram", 0.0);
            return macdData;
        }
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(closePrice, 12, 26);
        EMAIndicator signal = new EMAIndicator(macd, 9);
        
        int endIndex = series.getEndIndex();
        double macdValue = macd.getValue(endIndex).doubleValue();
        double signalValue = signal.getValue(endIndex).doubleValue();
        double histogram = macdValue - signalValue;
        
        macdData.put("macd", macdValue);
        macdData.put("signal", signalValue);
        macdData.put("histogram", histogram);
        
        return macdData;
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
            Map<String, Double> macdValues = calculateMACD(series);
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
            
            // MACD signals
            double macdValue = macdValues.get("macd");
            double macdSignal = macdValues.get("signal");
            double macdHistogram = macdValues.get("histogram");
            
            String macdTrend;
            if (macdHistogram > 0) {
                macdTrend = "Bullish";
            } else if (macdHistogram < 0) {
                macdTrend = "Bearish";
            } else {
                macdTrend = "Neutral";
            }
            signals.put("MACD", macdTrend + " (MACD: " + String.format("%.2f", macdValue) + ")");
            
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
            
            // Calculate simple momentum
            double momentum = 0;
            if (series.getBarCount() > 5) {
                double previousPrice = series.getBar(series.getEndIndex() - 5).getClosePrice().doubleValue();
                momentum = ((currentPrice / previousPrice) - 1) * 100;
                String momentumSignal = momentum > 0 ? "Positive" : "Negative";
                signals.put("Momentum", momentumSignal + " (" + String.format("%.2f", momentum) + "%)");
            }
            
            // Store results
            analysis.put("rsi", rsi);
            analysis.put("sma20", sma20);
            analysis.put("sma50", sma50);
            analysis.put("macd", macdValues);
            analysis.put("currentPrice", currentPrice);
            analysis.put("trend", trend);
            analysis.put("signals", signals);
            
        } catch (Exception e) {
            analysis.put("error", "Failed to generate technical analysis: " + e.getMessage());
        }
        
        return analysis;
    }
}
