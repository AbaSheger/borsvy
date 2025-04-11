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
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Random;
import java.util.stream.Collectors;

import com.borsvy.client.PolygonClient;
import com.borsvy.model.StockPrice;
import com.borsvy.model.NewsArticle;
import java.util.concurrent.CompletableFuture;

// Add TA4J imports
import org.ta4j.core.*;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

@Slf4j
@Service
public class AnalysisService {

    private final StockService stockService;
    private final StockAnalysisRepository analysisRepository;
    private final LLMAnalysisService llmAnalysisService;
    private final PolygonClient polygonClient;
    
    // In-memory cache for analysis results
    private final Map<String, Map<String, Object>> analysisCache = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastUpdateTimes = new ConcurrentHashMap<>();
    private static final int CACHE_DURATION_MINUTES = 30; // Cache duration in minutes
    
    @Autowired
    public AnalysisService(StockService stockService, StockAnalysisRepository analysisRepository, LLMAnalysisService llmAnalysisService, PolygonClient polygonClient) {
        this.stockService = stockService;
        this.analysisRepository = analysisRepository;
        this.llmAnalysisService = llmAnalysisService;
        this.polygonClient = polygonClient;
    }
    
    @Cacheable(value = "analysis", key = "#symbol")
    public Map<String, Object> getCompleteAnalysis(String symbol) {
        Map<String, Object> analysis = new HashMap<>();
        try {
            log.info("Starting complete analysis for symbol: {}", symbol);
            
            // Re-enable cache check
            Map<String, Object> cachedAnalysis = analysisCache.get(symbol);
            LocalDateTime lastUpdate = lastUpdateTimes.get(symbol);
            if (cachedAnalysis != null && lastUpdate != null && 
                LocalDateTime.now().minusMinutes(CACHE_DURATION_MINUTES).isBefore(lastUpdate)) {
                log.info("Returning cached analysis for symbol: {}", symbol);
                return cachedAnalysis;
            }
            
            // Validate symbol
            if (symbol == null || symbol.trim().isEmpty() || symbol.equals("undefined")) {
                log.error("Invalid symbol provided: {}", symbol);
                analysis.put("error", "Invalid stock symbol");
                return analysis;
            }
            
            // Get stock data
            Optional<Stock> stockOpt = stockService.getStockBySymbol(symbol);
            if (stockOpt.isEmpty()) {
                log.error("Stock not found for symbol: {}", symbol);
                analysis.put("error", "Stock not found");
                return analysis;
            }
            
            Stock stock = stockOpt.get();
            analysis.put("stock", stock);
            
            // Get news sentiment
            Map<String, Object> sentimentData = stockService.getNewsSentiment(symbol);
            analysis.put("newsSentiment", sentimentData);
            
            // Get LLM analysis
            Map<String, Object> llmAnalysis = llmAnalysisService.generateAnalysis(symbol);
            analysis.put("llmAnalysis", llmAnalysis);
            
            // Get technical analysis
            Map<String, Object> technicalAnalysis = getTechnicalAnalysis(stock);
            analysis.put("technical", technicalAnalysis);
            
            // Get fundamental analysis
            Map<String, Object> fundamentalAnalysis = getFundamentalAnalysis(stock);
            analysis.put("fundamental", fundamentalAnalysis);
            
            // Get news data
            List<NewsArticle> newsArticles = stockService.getStockNews(symbol, 5);
            List<Map<String, Object>> news = newsArticles.stream()
                .map(article -> {
                    Map<String, Object> articleMap = new HashMap<>();
                    articleMap.put("title", article.getTitle());
                    articleMap.put("url", article.getUrl());
                    articleMap.put("date", article.getPublishedDate());
                    articleMap.put("thumbnail", article.getThumbnail());
                    articleMap.put("source", article.getSource());
                    articleMap.put("summary", article.getSummary());
                    return articleMap;
                })
                .collect(Collectors.toList());
            analysis.put("recentNews", news);
            
            // Combine all analyses into a comprehensive summary
            String summary = generateComprehensiveSummary(technicalAnalysis, fundamentalAnalysis, llmAnalysis);
            analysis.put("summary", summary);
            
            // Add overall sentiment
            Map<String, Object> overallSentiment = determineOverallSentiment(technicalAnalysis, fundamentalAnalysis, llmAnalysis);
            analysis.put("overallSentiment", overallSentiment);
            
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
            // Use the TechnicalIndicatorService to get the analysis
            List<StockPrice> priceHistory = polygonClient.getHistoricalData(stock.getSymbol(), "1day");
            if (priceHistory == null || priceHistory.isEmpty()) {
                analysis.put("error", "No price history available");
                return analysis;
            }
            
            // Autowire the TechnicalIndicatorService
            TechnicalIndicatorService technicalIndicatorService = new TechnicalIndicatorService();
            
            // Get the technical analysis using the service
            Map<String, Object> technicalData = technicalIndicatorService.generateTechnicalAnalysis(priceHistory, stock.getSymbol());
            
            // Extract the required values
            double rsiValue = (double) technicalData.getOrDefault("rsi", 50.0);
            double sma20Value = (double) technicalData.getOrDefault("sma20", stock.getPrice());
            double sma50Value = (double) technicalData.getOrDefault("sma50", stock.getPrice());
            
            // Determine trend
            String trend = determineTrend(stock, sma20Value, sma50Value);
            
            // Generate technical signals
            Map<String, String> signals = generateTechnicalSignals(rsiValue, stock.getPrice(), sma20Value, sma50Value, stock);
            
            analysis.put("price", stock.getPrice());
            analysis.put("rsi", rsiValue);
            analysis.put("sma20", sma20Value);
            analysis.put("sma50", sma50Value);
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
    
    private String generateComprehensiveSummary(Map<String, Object> technical, Map<String, Object> fundamental, Object llmAnalysis) {
        StringBuilder summary = new StringBuilder();
        
        // Add LLM analysis
        if (llmAnalysis instanceof Map) {
            Map<?, ?> llmMap = (Map<?, ?>) llmAnalysis;
            if (llmMap.containsKey("analysis")) {
                summary.append("AI Analysis:\n").append(llmMap.get("analysis")).append("\n\n");
            }
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
    
    private Map<String, Object> determineOverallSentiment(Map<String, Object> technical, Map<String, Object> fundamental, Object llmAnalysis) {
        // Get sentiment from LLM analysis
        String llmSentiment = "neutral";
        double llmConfidence = 0.5;
        String llmAnalysisText = "";
        int llmScore = 0;
        
        if (llmAnalysis instanceof Map) {
            Map<?, ?> llmMap = (Map<?, ?>) llmAnalysis;
            if (llmMap.containsKey("sentiment")) {
                llmSentiment = (String) llmMap.get("sentiment");
                // Calculate LLM score based on sentiment and confidence
                if (llmMap.containsKey("confidence")) {
                    llmConfidence = ((Number) llmMap.get("confidence")).doubleValue();
                    if (llmSentiment.equals("positive")) {
                        llmScore = (int)(llmConfidence * 100);
                    } else if (llmSentiment.equals("negative")) {
                        llmScore = -(int)(llmConfidence * 100);
                    }
                }
            }
            if (llmMap.containsKey("analysis")) {
                llmAnalysisText = (String) llmMap.get("analysis");
            }
        }
        
        // Get technical trend
        String technicalTrend = (String) technical.getOrDefault("trend", "neutral");
        
        // Get fundamental signals
        Map<String, String> fundamentalSignals = (Map<String, String>) fundamental.getOrDefault("signals", new HashMap<>());
        
        // Calculate sentiment distribution
        int positiveCount = 0;
        int negativeCount = 0;
        int neutralCount = 0;
        
        // LLM sentiment contribution
        if (llmSentiment.equals("positive")) {
            positiveCount++;
        } else if (llmSentiment.equals("negative")) {
            negativeCount++;
        } else {
            neutralCount++;
        }
        
        // Technical trend contribution
        if (technicalTrend.equals("bullish")) {
            positiveCount++;
        } else if (technicalTrend.equals("bearish")) {
            negativeCount++;
        } else {
            neutralCount++;
        }
        
        // Fundamental signals contribution
        if (fundamentalSignals.containsKey("Valuation")) {
            String valuation = fundamentalSignals.get("Valuation");
            if (valuation.contains("undervalued")) {
                positiveCount++;
            } else if (valuation.contains("overvalued")) {
                negativeCount++;
            } else {
                neutralCount++;
            }
        } else {
            neutralCount++;
        }
        
        // Create detailed sentiment response
        Map<String, Object> sentimentResponse = new HashMap<>();
        sentimentResponse.put("sentiment", determineSentimentCategory(llmScore));
        sentimentResponse.put("score", llmScore);
        sentimentResponse.put("llmSentiment", llmSentiment);
        sentimentResponse.put("llmConfidence", llmConfidence);
        sentimentResponse.put("llmAnalysis", llmAnalysisText);
        sentimentResponse.put("technicalTrend", technicalTrend);
        sentimentResponse.put("fundamentalSignals", fundamentalSignals);
        
        // Ensure the same confidence value is used in both places by modifying the llmAnalysis object
        if (llmAnalysis instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> llmMap = (Map<String, Object>) llmAnalysis;
            // Update the confidence in the llmAnalysis to match the overallSentiment confidence
            if (llmMap.containsKey("confidence")) {
                llmMap.put("confidence", llmConfidence);
            }
        }
        
        // Add sentiment distribution
        Map<String, Integer> distribution = new HashMap<>();
        distribution.put("positive", positiveCount);
        distribution.put("negative", negativeCount);
        distribution.put("neutral", neutralCount);
        sentimentResponse.put("distribution", distribution);
        
        // Add detailed analysis breakdown
        List<Map<String, Object>> analysisBreakdown = new ArrayList<>();
        
        // Add LLM analysis
        Map<String, Object> llmBreakdown = new HashMap<>();
        llmBreakdown.put("source", "AI Analysis");
        llmBreakdown.put("sentiment", llmSentiment);
        llmBreakdown.put("confidence", llmConfidence);
        llmBreakdown.put("score", llmScore);
        llmBreakdown.put("analysis", llmAnalysisText);
        analysisBreakdown.add(llmBreakdown);
        
        // Add technical analysis
        Map<String, Object> technicalBreakdown = new HashMap<>();
        technicalBreakdown.put("source", "Technical Analysis");
        technicalBreakdown.put("sentiment", technicalTrend.equals("bullish") ? "positive" : technicalTrend.equals("bearish") ? "negative" : "neutral");
        technicalBreakdown.put("analysis", "Based on technical indicators and price trends");
        analysisBreakdown.add(technicalBreakdown);
        
        // Add fundamental analysis
        Map<String, Object> fundamentalBreakdown = new HashMap<>();
        fundamentalBreakdown.put("source", "Fundamental Analysis");
        String fundamentalSentiment = "neutral";
        if (fundamentalSignals.containsKey("Valuation")) {
            fundamentalSentiment = fundamentalSignals.get("Valuation").contains("undervalued") ? "positive" : 
                                 fundamentalSignals.get("Valuation").contains("overvalued") ? "negative" : "neutral";
        }
        fundamentalBreakdown.put("sentiment", fundamentalSentiment);
        fundamentalBreakdown.put("analysis", "Based on valuation metrics and financial indicators");
        analysisBreakdown.add(fundamentalBreakdown);
        
        sentimentResponse.put("analysisBreakdown", analysisBreakdown);
        
        return sentimentResponse;
    }
    
    private String determineSentimentCategory(int score) {
        if (score > 50) return "strongly positive";
        else if (score > 20) return "positive";
        else if (score < -50) return "strongly negative";
        else if (score < -20) return "negative";
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
        // Use the market cap directly from the stock object
        // as it's already set from the API response
        return stock.getMarketCap();
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
        
        // Dividend yield signals - Note: Limited in free tier
        if (dividendYield > 3) {
            signals.put("Dividend", "High yield");
        } else if (dividendYield > 0) {
            signals.put("Dividend", "Moderate yield");
        } else {
            signals.put("Dividend", "Data not available");
        }
        
        // Market cap signals
        if (marketCap >= 1000) { // > $1T (1000B)
            signals.put("Market Cap", "Mega Cap");
        } else if (marketCap >= 200) { // > $200B
            signals.put("Market Cap", "Large Cap");
        } else if (marketCap >= 10) { // > $10B
            signals.put("Market Cap", "Mid Cap");
        } else if (marketCap >= 2) { // > $2B
            signals.put("Market Cap", "Small Cap");
        } else {
            signals.put("Market Cap", "Micro Cap");
        }
        
        return signals;
    }

    public List<Map<String, Object>> getPriceHistory(String symbol, String interval) {
        if (symbol == null || symbol.trim().isEmpty()) {
            log.error("Invalid symbol provided for price history: {}", symbol);
            return Collections.emptyList();
        }

        try {
            // Get historical data from Polygon API
            List<StockPrice> priceData = polygonClient.getHistoricalData(symbol, interval);

            if (priceData.isEmpty()) {
                // If no data from Polygon, try to generate mock data
                return generateMockPriceHistory(interval);
            }

            // Convert StockPrice objects to Map<String, Object>
            return priceData.stream()
                .map(price -> {
                    Map<String, Object> point = new HashMap<>();
                    point.put("timestamp", price.getTimestamp().toString());
                    point.put("price", price.getPrice());
                    point.put("volume", price.getVolume());
                    return point;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error fetching price history for symbol: " + symbol, e);
            // Return mock data in case of error
            return generateMockPriceHistory(interval);
        }
    }

    private String formatInterval(String interval) {
        switch (interval.toUpperCase()) {
            case "1D": return "1day";
            case "1W": return "1week";
            case "1M": return "1month";
            case "3M": return "3month";
            case "1Y": return "1year";
            default: return "1day";
        }
    }

    private LocalDateTime calculateStartDate(LocalDateTime endDate, String interval) {
        switch (interval.toUpperCase()) {
            case "1D": return endDate.minusDays(1);
            case "1W": return endDate.minusWeeks(1);
            case "1M": return endDate.minusMonths(1);
            case "3M": return endDate.minusMonths(3);
            case "1Y": return endDate.minusYears(1);
            default: return endDate.minusDays(1);
        }
    }

    private List<Map<String, Object>> generateMockPriceHistory(String interval) {
        List<Map<String, Object>> mockData = new ArrayList<>();
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = calculateStartDate(endDate, interval);
        
        // Generate mock price points
        double basePrice = 100.0;
        Random random = new Random();
        
        while (startDate.isBefore(endDate)) {
            Map<String, Object> point = new HashMap<>();
            point.put("timestamp", startDate.toString());
            point.put("price", basePrice + random.nextDouble() * 10 - 5); // Random price ±5 from base
            mockData.add(point);
            
            // Increment based on interval
            switch (interval.toUpperCase()) {
                case "1D": startDate = startDate.plusHours(1); break;
                case "1W": startDate = startDate.plusDays(1); break;
                case "1M": startDate = startDate.plusDays(1); break;
                case "3M": startDate = startDate.plusDays(3); break;
                case "1Y": startDate = startDate.plusWeeks(1); break;
                default: startDate = startDate.plusHours(1);
            }
        }
        
        return mockData;
    }
}
