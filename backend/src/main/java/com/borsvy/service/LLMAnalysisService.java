package com.borsvy.service;

import com.borsvy.model.Stock;
import com.borsvy.model.StockAnalysis;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

@Slf4j
@Service
public class LLMAnalysisService {

    private final RestTemplate restTemplate;
    private final StockService stockService;
    private final ObjectMapper objectMapper;
    
    @Value("${huggingface.api.key}")
    private String apiKey;
    
    private static final String API_URL = "https://api-inference.huggingface.co/models/mistralai/Mistral-7B-Instruct-v0.2";
    private static final int MAX_REQUESTS_PER_MINUTE = 30;
    private int requestCount = 0;
    private long lastResetTime = System.currentTimeMillis();
    
    @Autowired
    public LLMAnalysisService(RestTemplate restTemplate, StockService stockService, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.stockService = stockService;
        this.objectMapper = objectMapper;
        log.info("LLMAnalysisService initialized with API key length: {}", apiKey != null ? apiKey.length() : 0);
    }
    
    public Map<String, Object> generateAnalysis(String symbol) {
        log.debug("Starting LLM analysis for symbol: {}", symbol);
        
        try {
            // Check rate limit
            checkRateLimit();
            
            // Get stock data
            Stock stock = stockService.getStockBySymbol(symbol)
                .orElseThrow(() -> new RuntimeException("Stock not found"));
            
            log.debug("Retrieved stock data for {}: price=${}, change={}%", 
                symbol, stock.getPrice(), stock.getChangePercent());
            
            // Generate prompt for analysis
            String prompt = generatePrompt(stock);
            log.debug("Generated prompt for analysis");
            
            // Make API request
            log.debug("Making API request to Hugging Face");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("inputs", prompt);
            
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                API_URL,
                HttpMethod.POST,
                requestEntity,
                String.class
            );
            
            log.debug("Received response from Hugging Face API: {}", response.getBody());
            
            // Parse response
            Map<String, Object> sentimentResult = extractSentiment(response.getBody());
            String sentiment = (String) sentimentResult.get("sentiment");
            Double confidence = (Double) sentimentResult.get("confidence");
            
            // Generate analysis text
            String analysis = generateAnalysisText(stock, sentiment, confidence);
            
            // Create response
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("sentiment", sentiment);
            responseMap.put("confidence", confidence);
            responseMap.put("analysis", analysis);
            
            log.info("Successfully generated LLM analysis for {} with sentiment: {} and confidence: {}", 
                symbol, sentiment, confidence);
            
            return responseMap;
            
        } catch (Exception e) {
            log.error("Error generating LLM analysis for {}: {}", symbol, e.getMessage(), e);
            throw new RuntimeException("Failed to generate analysis: " + e.getMessage());
        }
    }
    
    private void checkRateLimit() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastResetTime > 60000) { // Reset after 1 minute
            requestCount = 0;
            lastResetTime = currentTime;
        }
        
        if (requestCount >= MAX_REQUESTS_PER_MINUTE) {
            throw new RuntimeException("Rate limit exceeded. Please try again later.");
        }
        
        requestCount++;
        log.debug("Rate limit counter incremented to: {}", requestCount);
    }
    
    private String generatePrompt(Stock stock) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze the sentiment of the following stock market data and return ONLY the sentiment (POSITIVE, NEGATIVE, or NEUTRAL) and a confidence score between 0 and 1. Format your response exactly like this: SENTIMENT:POSITIVE CONFIDENCE:0.85\n\n");
        prompt.append("Stock Data:\n");
        prompt.append("Symbol: ").append(stock.getSymbol()).append("\n");
        prompt.append("Price: $").append(stock.getPrice()).append("\n");
        prompt.append("Change: ").append(stock.getChangePercent()).append("%\n");
        prompt.append("Volume: ").append(stock.getVolume()).append("\n");
        
        // Add additional context for new stocks
        if (stock.getPrice() == 0.0 && stock.getVolume() == 0L) {
            prompt.append("Note: This is a newly added stock with default values. ");
            prompt.append("Consider this when analyzing the sentiment.");
        }
        
        return prompt.toString();
    }
    
    private Map<String, Object> extractSentiment(String response) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Extract sentiment and confidence from the response
            String[] parts = response.split("\\s+");
            String sentiment = null;
            Double confidence = 0.0;
            
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].startsWith("SENTIMENT:")) {
                    sentiment = parts[i].substring("SENTIMENT:".length()).toLowerCase();
                } else if (parts[i].startsWith("CONFIDENCE:")) {
                    try {
                        confidence = Double.parseDouble(parts[i].substring("CONFIDENCE:".length()));
                    } catch (NumberFormatException e) {
                        confidence = 0.5; // Default confidence if parsing fails
                    }
                }
            }
            
            if (sentiment == null) {
                sentiment = "neutral";
                confidence = 0.5;
            }
            
            result.put("sentiment", sentiment);
            result.put("confidence", confidence);
            
        } catch (Exception e) {
            log.error("Error parsing Mistral-7B response: {}", e.getMessage());
            result.put("sentiment", "neutral");
            result.put("confidence", 0.5);
        }
        
        return result;
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
        
        // Add recommendation
        analysis.append("\nRecommendation:\n");
        if (sentiment.equals("positive") && confidence > 0.7) {
            analysis.append("Consider buying or holding the stock.\n");
        } else if (sentiment.equals("negative") && confidence > 0.7) {
            analysis.append("Consider selling or avoiding the stock.\n");
        } else {
            analysis.append("Monitor the stock for clearer signals.\n");
        }
        
        return analysis.toString();
    }
} 