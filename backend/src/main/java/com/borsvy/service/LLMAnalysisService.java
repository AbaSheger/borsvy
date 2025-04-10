package com.borsvy.service;

import com.borsvy.model.Stock;
import com.borsvy.model.StockAnalysis;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.LocalDateTime;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Comparator;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Service
public class LLMAnalysisService {

    private final RestTemplate restTemplate;
    private final StockService stockService;
    private final ObjectMapper objectMapper;
    
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/models";
    
    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.model.id}")
    private String modelId;
    
    @Autowired
    public LLMAnalysisService(RestTemplate restTemplate, StockService stockService, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.stockService = stockService;
        this.objectMapper = objectMapper;
        log.info("LLMAnalysisService initialized");
    }
    
    public Map<String, Object> generateAnalysis(String symbol) {
        log.debug("Starting analysis for symbol: {}", symbol);
        
        try {
            Stock stock = stockService.getStockBySymbol(symbol)
                .orElseThrow(() -> new RuntimeException("Stock not found"));
            
            String marketAnalysis = generateMarketBasedAnalysis(stock);
            String sentiment = getSentimentFromGroq(marketAnalysis);
            double confidence = calculateConfidence(stock, sentiment);
            String analysis = generateAnalysisText(stock, sentiment, confidence);
            
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("sentiment", sentiment);
            responseMap.put("confidence", confidence);
            responseMap.put("analysis", analysis);
            
            log.info("Successfully generated analysis for {} with sentiment: {} and confidence: {}", 
                symbol, sentiment, confidence);
            
            return responseMap;
            
        } catch (Exception e) {
            log.error("Error generating analysis for {}: {}", symbol, e.getMessage(), e);
            throw new RuntimeException("Failed to generate analysis: " + e.getMessage());
        }
    }
    
    private String getSentimentFromGroq(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelId); // Include the model ID
            requestBody.put("inputs", text);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                GROQ_API_URL,
                HttpMethod.POST,
                requestEntity,
                String.class
            );
            
            // Parse the response
            List<Map<String, Object>> responseList = objectMapper.readValue(response.getBody(), 
                new TypeReference<List<Map<String, Object>>>() {});
            
            if (!responseList.isEmpty()) {
                Map<String, Object> result = responseList.get(0);
                if (result.containsKey("label")) {
                    return ((String) result.get("label")).toUpperCase();
                }
            }
            
            return "NEUTRAL";
            
        } catch (Exception e) {
            log.error("Error getting sentiment from Groq: {}", e.getMessage());
            return "NEUTRAL";
        }
    }
    
    private String generateMarketBasedAnalysis(Stock stock) {
        StringBuilder analysis = new StringBuilder();
        
        // Price movement analysis
        if (stock.getChangePercent() > 2.0) {
            analysis.append("Strong positive price movement. ");
        } else if (stock.getChangePercent() > 0) {
            analysis.append("Slight positive price movement. ");
        } else if (stock.getChangePercent() < -2.0) {
            analysis.append("Strong negative price movement. ");
        } else if (stock.getChangePercent() < 0) {
            analysis.append("Slight negative price movement. ");
        } else {
            analysis.append("Price is stable. ");
        }
        
        // Volume analysis
        if (stock.getVolume() > 1000000) {
            analysis.append("High trading volume indicates strong market interest. ");
        } else if (stock.getVolume() > 100000) {
            analysis.append("Moderate trading volume suggests normal market activity. ");
        } else {
            analysis.append("Low trading volume may indicate limited market interest. ");
        }
        
        // Technical indicators
        if (stock.getMacd() > 0) {
            analysis.append("MACD shows bullish momentum. ");
        } else if (stock.getMacd() < 0) {
            analysis.append("MACD shows bearish momentum. ");
        }
        
        if (stock.getRsi() > 70) {
            analysis.append("RSI indicates overbought conditions. ");
        } else if (stock.getRsi() < 30) {
            analysis.append("RSI indicates oversold conditions. ");
        }
        
        return analysis.toString();
    }
    
    private double calculateConfidence(Stock stock, String sentiment) {
        double confidence = 0.5; // Start with neutral confidence
        
        // Factor 1: Price movement strength
        double priceMovement = Math.abs(stock.getChangePercent());
        confidence += Math.min(priceMovement / 10.0, 0.2);
        
        // Factor 2: Volume significance
        if (stock.getVolume() > 1000000) confidence += 0.15;
        else if (stock.getVolume() > 100000) confidence += 0.1;
        else if (stock.getVolume() > 10000) confidence += 0.05;
        
        // Factor 3: Technical indicators alignment
        if (stock.getRsi() < 30 || stock.getRsi() > 70) confidence += 0.05;
        if (stock.getMacd() > 0) confidence += 0.05;
        if (stock.getPrice() > stock.getSma20()) confidence += 0.05;
        
        // Ensure confidence stays between 0.1 and 0.9
        return Math.max(0.1, Math.min(0.9, confidence));
    }
    
    private String generateAnalysisText(Stock stock, String sentiment, Double confidence) {
        StringBuilder analysis = new StringBuilder();
        
        // Add sentiment analysis
        analysis.append("Based on the current market data, the sentiment is ");
        analysis.append(sentiment).append(" with ");
        analysis.append(String.format("%.1f", confidence * 100)).append("% confidence.\n\n");
        
        // Add price movement analysis
        if (stock.getPrice() > 0) {
            analysis.append("Price Movement:\n");
            analysis.append("Current Price: $").append(stock.getPrice()).append("\n");
            analysis.append("Change: ").append(stock.getChangePercent()).append("%\n");
            
            if (stock.getChangePercent() > 0) {
                analysis.append("The stock is showing positive momentum.\n");
            } else if (stock.getChangePercent() < 0) {
                analysis.append("The stock is showing negative momentum.\n");
            } else {
                analysis.append("The stock price is stable.\n");
            }
        }
        
        // Add volume analysis
        if (stock.getVolume() > 0) {
            analysis.append("\nVolume Analysis:\n");
            analysis.append("Current Volume: ").append(stock.getVolume()).append("\n");
            
            if (stock.getVolume() > 1000000) {
                analysis.append("High trading volume indicates strong market interest.\n");
            } else if (stock.getVolume() > 100000) {
                analysis.append("Moderate trading volume suggests normal market activity.\n");
            } else {
                analysis.append("Low trading volume may indicate limited market interest.\n");
            }
        }
        
        // Add technical indicators
        analysis.append("\nTechnical Indicators:\n");
        if (stock.getMacd() != 0) {
            analysis.append("MACD: ").append(stock.getMacd() > 0 ? "Bullish" : "Bearish").append("\n");
        }
        if (stock.getRsi() > 0) {
            analysis.append("RSI: ").append(stock.getRsi()).append("\n");
            if (stock.getRsi() > 70) {
                analysis.append("RSI indicates overbought conditions.\n");
            } else if (stock.getRsi() < 30) {
                analysis.append("RSI indicates oversold conditions.\n");
            }
        }
        
        // Add recommendation
        analysis.append("\nRecommendation:\n");
        if (sentiment.equals("POSITIVE") && confidence > 0.7) {
            analysis.append("Consider buying or holding the stock.\n");
        } else if (sentiment.equals("NEGATIVE") && confidence > 0.7) {
            analysis.append("Consider selling or avoiding the stock.\n");
        } else {
            analysis.append("Monitor the stock for clearer signals.\n");
        }
        
        return analysis.toString();
    }
} 