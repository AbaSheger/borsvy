package com.borsvy.service;

import com.borsvy.model.Stock;
import com.borsvy.model.StockAnalysis;
import com.borsvy.repository.StockAnalysisRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AnalysisService {

    private final StockService stockService;
    private final StockAnalysisRepository analysisRepository;
    private final LLMAnalysisService llmAnalysisService;
    
    // In-memory cache for analysis results
    private final Map<String, Map<String, Object>> analysisCache = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastUpdateTimes = new ConcurrentHashMap<>();
    private static final int CACHE_DURATION_MINUTES = 30; // Cache duration in minutes
    
    @Autowired
    public AnalysisService(StockService stockService, StockAnalysisRepository analysisRepository, LLMAnalysisService llmAnalysisService) {
        this.stockService = stockService;
        this.analysisRepository = analysisRepository;
        this.llmAnalysisService = llmAnalysisService;
    }
    
    @Cacheable(value = "analysis", key = "#symbol")
    public Map<String, Object> getCompleteAnalysis(String symbol) {
        Map<String, Object> analysis = new HashMap<>();
        try {
            log.info("Starting complete analysis for symbol: {}", symbol);
            
            // Check cache first
            Map<String, Object> cachedAnalysis = analysisCache.get(symbol);
            LocalDateTime lastUpdate = lastUpdateTimes.get(symbol);
            
            if (cachedAnalysis != null && lastUpdate != null) {
                if (LocalDateTime.now().minusMinutes(CACHE_DURATION_MINUTES).isBefore(lastUpdate)) {
                    log.info("Returning cached analysis for symbol: {}", symbol);
                    return cachedAnalysis;
                }
            }
            
            // Get stock data
            Optional<Stock> stockOpt = stockService.getStockBySymbol(symbol);
            if (stockOpt.isEmpty()) {
                log.error("Stock not found for symbol: {}", symbol);
                analysis.put("error", "Stock not found");
                return analysis;
            }
            
            Stock stock = stockOpt.get();
            
            // Get technical analysis
            Map<String, Object> technicalAnalysis = getTechnicalAnalysis(stock);
            analysis.put("technical", technicalAnalysis);
            
            // Get fundamental analysis
            Map<String, Object> fundamentalAnalysis = getFundamentalAnalysis(stock);
            analysis.put("fundamental", fundamentalAnalysis);
            
            // Get LLM analysis
            Map<String, Object> llmAnalysis = llmAnalysisService.generateAnalysis(symbol);
            analysis.put("llm", llmAnalysis);
            
            // Get news data
            List<Map<String, Object>> news = stockService.getStockNews(symbol, 5);
            analysis.put("recentNews", news);
            
            // Get news sentiment
            Map<String, Object> newsSentiment = stockService.getNewsSentiment(symbol);
            analysis.put("newsSentiment", newsSentiment);
            
            // Combine all analyses into a comprehensive summary
            String summary = generateComprehensiveSummary(technicalAnalysis, fundamentalAnalysis, llmAnalysis);
            analysis.put("summary", summary);
            
            // Add overall sentiment
            analysis.put("overallSentiment", determineOverallSentiment(technicalAnalysis, fundamentalAnalysis, llmAnalysis));
            
            // Update cache
            analysisCache.put(symbol, analysis);
            lastUpdateTimes.put(symbol, LocalDateTime.now());
            
            log.info("Completed analysis for symbol: {}", symbol);
            
        } catch (Exception e) {
            log.error("Error generating complete analysis for {}: {}", symbol, e.getMessage(), e);
            analysis.put("error", "Failed to generate analysis: " + e.getMessage());
        }
        
        return analysis;
    }
    
    // Scheduled task to clean up old cache entries
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void cleanupCache() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(CACHE_DURATION_MINUTES);
        
        analysisCache.entrySet().removeIf(entry -> {
            LocalDateTime lastUpdate = lastUpdateTimes.get(entry.getKey());
            if (lastUpdate != null && lastUpdate.isBefore(cutoffTime)) {
                lastUpdateTimes.remove(entry.getKey());
                return true;
            }
            return false;
        });
        
        log.info("Cache cleanup completed. Current cache size: {}", analysisCache.size());
    }
    
    // Method to manually clear cache for a specific symbol
    public void clearCacheForSymbol(String symbol) {
        analysisCache.remove(symbol);
        lastUpdateTimes.remove(symbol);
        log.info("Cleared cache for symbol: {}", symbol);
    }
    
    private Map<String, Object> getTechnicalAnalysis(Stock stock) {
        Map<String, Object> analysis = new HashMap<>();
        try {
            // Calculate technical indicators
            double rsi = calculateRSI(stock);
            double macd = calculateMACD(stock);
            double sma20 = calculateSMA(stock, 20);
            double sma50 = calculateSMA(stock, 50);
            
            // Determine trend
            String trend = determineTrend(stock, sma20, sma50);
            
            // Generate technical signals
            Map<String, String> signals = generateTechnicalSignals(rsi, macd, sma20, sma50, stock);
            
            analysis.put("rsi", rsi);
            analysis.put("macd", macd);
            analysis.put("sma20", sma20);
            analysis.put("sma50", sma50);
            analysis.put("trend", trend);
            analysis.put("signals", signals);
            
        } catch (Exception e) {
            log.error("Error in technical analysis: {}", e.getMessage(), e);
            analysis.put("error", "Technical analysis failed");
        }
        
        return analysis;
    }
    
    private Map<String, Object> getFundamentalAnalysis(Stock stock) {
        Map<String, Object> analysis = new HashMap<>();
        try {
            // Calculate fundamental metrics
            double peRatio = calculatePERatio(stock);
            double dividendYield = calculateDividendYield(stock);
            double marketCap = calculateMarketCap(stock);
            
            // Generate fundamental signals
            Map<String, String> signals = generateFundamentalSignals(peRatio, dividendYield, marketCap);
            
            analysis.put("peRatio", peRatio);
            analysis.put("dividendYield", dividendYield);
            analysis.put("marketCap", marketCap);
            analysis.put("signals", signals);
            
        } catch (Exception e) {
            log.error("Error in fundamental analysis: {}", e.getMessage(), e);
            analysis.put("error", "Fundamental analysis failed");
        }
        
        return analysis;
    }
    
    private String generateComprehensiveSummary(Map<String, Object> technical, Map<String, Object> fundamental, Map<String, Object> llm) {
        StringBuilder summary = new StringBuilder();
        
        // Add LLM analysis
        if (llm.containsKey("analysis")) {
            summary.append("AI Analysis:\n").append(llm.get("analysis")).append("\n\n");
        }
        
        // Add technical analysis summary
        if (technical.containsKey("trend") && technical.containsKey("signals")) {
            summary.append("Technical Analysis:\n");
            summary.append("Current Trend: ").append(technical.get("trend")).append("\n");
            Map<String, String> signals = (Map<String, String>) technical.get("signals");
            signals.forEach((key, value) -> summary.append(key).append(": ").append(value).append("\n"));
            summary.append("\n");
        }
        
        // Add fundamental analysis summary
        if (fundamental.containsKey("signals")) {
            summary.append("Fundamental Analysis:\n");
            Map<String, String> signals = (Map<String, String>) fundamental.get("signals");
            signals.forEach((key, value) -> summary.append(key).append(": ").append(value).append("\n"));
        }
        
        return summary.toString();
    }
    
    private String determineOverallSentiment(Map<String, Object> technical, Map<String, Object> fundamental, Map<String, Object> llm) {
        // Get sentiment from LLM analysis
        String llmSentiment = (String) llm.getOrDefault("sentiment", "neutral");
        Double llmConfidence = (Double) llm.getOrDefault("confidence", 0.5);
        
        // Get technical trend
        String technicalTrend = (String) technical.getOrDefault("trend", "neutral");
        
        // Get fundamental signals
        Map<String, String> fundamentalSignals = (Map<String, String>) fundamental.getOrDefault("signals", new HashMap<>());
        
        // Weight the different analyses
        int sentimentScore = 0;
        
        // LLM sentiment weight (based on confidence)
        if (llmSentiment.equals("positive")) sentimentScore += (int)(llmConfidence * 100);
        else if (llmSentiment.equals("negative")) sentimentScore -= (int)(llmConfidence * 100);
        
        // Technical trend weight
        if (technicalTrend.equals("bullish")) sentimentScore += 30;
        else if (technicalTrend.equals("bearish")) sentimentScore -= 30;
        
        // Fundamental signals weight
        if (fundamentalSignals.containsKey("Valuation")) {
            String valuation = fundamentalSignals.get("Valuation");
            if (valuation.contains("undervalued")) sentimentScore += 20;
            else if (valuation.contains("overvalued")) sentimentScore -= 20;
        }
        
        // Determine overall sentiment
        if (sentimentScore > 50) return "strongly positive";
        else if (sentimentScore > 20) return "positive";
        else if (sentimentScore < -50) return "strongly negative";
        else if (sentimentScore < -20) return "negative";
        else return "neutral";
    }
    
    private void saveAnalysisToDatabase(String symbol, Map<String, Object> analysis, Map<String, Object> newsSentiment) {
        try {
            StockAnalysis stockAnalysis = new StockAnalysis();
            stockAnalysis.setSymbol(symbol);
            stockAnalysis.setTimestamp(LocalDateTime.now());
            stockAnalysis.setRecommendation((String) analysis.get("recommendation"));
            stockAnalysis.setSentiment((String) analysis.get("sentiment"));
            
            // Add news sentiment if available
            if (newsSentiment != null && newsSentiment.containsKey("sentiment")) {
                stockAnalysis.setNewsSentiment((String) newsSentiment.get("sentiment"));
            }
            
            analysisRepository.save(stockAnalysis);
        } catch (Exception e) {
            log.error("Error saving analysis to database: {}", e.getMessage());
        }
    }
    
    public Map<String, Object> getHistoricalAnalysis(String symbol) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<StockAnalysis> analyses = analysisRepository.findBySymbolOrderByTimestampDesc(symbol);
            result.put("analyses", analyses);
            return result;
        } catch (Exception e) {
            log.error("Error fetching historical analyses: {}", e.getMessage());
            result.put("error", "Failed to fetch historical analyses");
            return result;
        }
    }
    
    private double calculateRSI(Stock stock) {
        // Simple RSI calculation (14-day period)
        double avgGain = 0;
        double avgLoss = 0;
        
        // For demo purposes, using a simplified calculation
        if (stock.getChangePercent() > 0) {
            avgGain = stock.getChangePercent();
            avgLoss = 0;
        } else {
            avgGain = 0;
            avgLoss = Math.abs(stock.getChangePercent());
        }
        
        double rs = avgGain / (avgLoss == 0 ? 1 : avgLoss);
        return 100 - (100 / (1 + rs));
    }
    
    private double calculateMACD(Stock stock) {
        // Simplified MACD calculation
        double ema12 = stock.getPrice() * 1.1; // Simulated 12-day EMA
        double ema26 = stock.getPrice() * 0.95; // Simulated 26-day EMA
        return ema12 - ema26;
    }
    
    private double calculateSMA(Stock stock, int period) {
        // Simplified SMA calculation
        return stock.getPrice() * (1 + (stock.getChangePercent() / 100));
    }
    
    private String determineTrend(Stock stock, double sma20, double sma50) {
        if (stock.getPrice() > sma20 && sma20 > sma50) {
            return "bullish";
        } else if (stock.getPrice() < sma20 && sma20 < sma50) {
            return "bearish";
        } else {
            return "neutral";
        }
    }
    
    private Map<String, String> generateTechnicalSignals(double rsi, double macd, double sma20, double sma50, Stock stock) {
        Map<String, String> signals = new HashMap<>();
        
        // RSI signals
        if (rsi > 70) {
            signals.put("RSI", "Overbought");
        } else if (rsi < 30) {
            signals.put("RSI", "Oversold");
        } else {
            signals.put("RSI", "Neutral");
        }
        
        // MACD signals
        if (macd > 0) {
            signals.put("MACD", "Bullish");
        } else if (macd < 0) {
            signals.put("MACD", "Bearish");
        } else {
            signals.put("MACD", "Neutral");
        }
        
        // Moving Average signals
        if (stock.getPrice() > sma20) {
            signals.put("Moving Averages", "Above 20-day SMA");
        } else {
            signals.put("Moving Averages", "Below 20-day SMA");
        }
        
        return signals;
    }
    
    private double calculatePERatio(Stock stock) {
        // Simplified P/E ratio calculation
        return stock.getPrice() / (stock.getEarningsPerShare() == 0 ? 1 : stock.getEarningsPerShare());
    }
    
    private double calculateDividendYield(Stock stock) {
        // Simplified dividend yield calculation
        return (stock.getDividend() / stock.getPrice()) * 100;
    }
    
    private double calculateMarketCap(Stock stock) {
        // Simplified market cap calculation
        return stock.getPrice() * stock.getSharesOutstanding();
    }
    
    private Map<String, String> generateFundamentalSignals(double peRatio, double dividendYield, double marketCap) {
        Map<String, String> signals = new HashMap<>();
        
        // P/E ratio signals
        if (peRatio < 15) {
            signals.put("Valuation", "Potentially undervalued");
        } else if (peRatio > 30) {
            signals.put("Valuation", "Potentially overvalued");
        } else {
            signals.put("Valuation", "Fairly valued");
        }
        
        // Dividend yield signals
        if (dividendYield > 3) {
            signals.put("Dividend", "High yield");
        } else if (dividendYield > 0) {
            signals.put("Dividend", "Moderate yield");
        } else {
            signals.put("Dividend", "No dividend");
        }
        
        // Market cap signals
        if (marketCap > 1000000000000L) { // > $1T
            signals.put("Market Cap", "Large Cap");
        } else if (marketCap > 10000000000L) { // > $10B
            signals.put("Market Cap", "Mid Cap");
        } else {
            signals.put("Market Cap", "Small Cap");
        }
        
        return signals;
    }
}
