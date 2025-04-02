package com.borsvy.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@Component
public class SerpApiClient {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${serpapi.api.key}")
    private String apiKey;
    
    @Value("${serpapi.api.url:https://serpapi.com/search}")
    private String apiUrl;
    
    @Autowired
    public SerpApiClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Gets news sentiment analysis for a given stock symbol by analyzing recent news headlines
     */
    public Map<String, Object> getNewsSentiment(String symbol) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Create URL for SerpAPI news search
            String url = apiUrl + 
                    "?engine=google_news" + 
                    "&q=" + symbol + "+stock" + 
                    "&tbm=nws" + 
                    "&num=10" + 
                    "&api_key=" + apiKey;
            
            log.debug("Calling SerpAPI for news sentiment: {}", url.replace(apiKey, "API_KEY_REDACTED"));
            
            String response = restTemplate.getForObject(url, String.class);
            result.put("data", response);
            
            // Process the news results to analyze sentiment
            JsonNode rootNode = objectMapper.readTree(response);
            if (rootNode.has("news_results")) {
                JsonNode newsResults = rootNode.get("news_results");
                
                int positiveCount = 0;
                int negativeCount = 0;
                int neutralCount = 0;
                
                // Simple sentiment analysis on headlines and snippets
                for (JsonNode news : newsResults) {
                    String title = news.has("title") ? news.get("title").asText().toLowerCase() : "";
                    String snippet = news.has("snippet") ? news.get("snippet").asText().toLowerCase() : "";
                    
                    // Check for positive keywords
                    if (containsPositiveKeywords(title + " " + snippet)) {
                        positiveCount++;
                    } 
                    // Check for negative keywords
                    else if (containsNegativeKeywords(title + " " + snippet)) {
                        negativeCount++;
                    } 
                    // Otherwise neutral
                    else {
                        neutralCount++;
                    }
                }
                
                // Determine overall sentiment
                String sentiment;
                if (positiveCount > negativeCount + neutralCount) {
                    sentiment = "bullish";
                } else if (negativeCount > positiveCount + neutralCount) {
                    sentiment = "bearish";
                } else if (positiveCount > negativeCount) {
                    sentiment = "slightly bullish";
                } else if (negativeCount > positiveCount) {
                    sentiment = "slightly bearish";
                } else {
                    sentiment = "neutral";
                }
                
                result.put("sentiment", sentiment);
                result.put("positiveCount", positiveCount);
                result.put("negativeCount", negativeCount);
                result.put("neutralCount", neutralCount);
            }
            
        } catch (Exception e) {
            log.error("Error fetching news sentiment from SerpAPI: {}", e.getMessage());
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Gets recent news articles for a stock
     */
    public List<Map<String, Object>> getStockNews(String symbol, int limit) {
        List<Map<String, Object>> newsArticles = new ArrayList<>();
        
        try {
            // Create URL for SerpAPI news search
            String url = apiUrl + 
                    "?engine=google_news" + 
                    "&q=" + symbol + "+stock" + 
                    "&tbm=nws" + 
                    "&num=" + limit + 
                    "&api_key=" + apiKey;
            
            log.debug("Calling SerpAPI for news: {}", url.replace(apiKey, "API_KEY_REDACTED"));
            
            String response = restTemplate.getForObject(url, String.class);
            log.debug("SerpAPI response: {}", response);
            
            // Extract news articles
            JsonNode rootNode = objectMapper.readTree(response);
            if (rootNode.has("news_results")) {
                JsonNode newsResults = rootNode.get("news_results");
                log.debug("Found {} news results", newsResults.size());
                
                for (JsonNode news : newsResults) {
                    Map<String, Object> article = new HashMap<>();
                    
                    // Extract title
                    if (news.has("title")) {
                        article.put("title", news.get("title").asText());
                    }
                    
                    // Extract URL
                    if (news.has("link")) {
                        article.put("url", news.get("link").asText());
                    }
                    
                    // Extract source
                    if (news.has("source")) {
                        JsonNode source = news.get("source");
                        if (source.has("name")) {
                            article.put("source", source.get("name").asText());
                        }
                    }
                    
                    // Extract date
                    if (news.has("date")) {
                        article.put("date", news.get("date").asText());
                    }
                    
                    // Extract snippet as summary
                    if (news.has("snippet")) {
                        article.put("summary", news.get("snippet").asText());
                    }
                    
                    // Extract thumbnail
                    if (news.has("thumbnail")) {
                        article.put("thumbnail", news.get("thumbnail").asText());
                    }
                    
                    newsArticles.add(article);
                    log.debug("Added article: {}", article);
                    
                    if (newsArticles.size() >= limit) break;
                }
            } else {
                log.warn("No news_results found in SerpAPI response");
            }
            
        } catch (Exception e) {
            log.error("Error fetching news from SerpAPI: {}", e.getMessage());
            Map<String, Object> errorArticle = new HashMap<>();
            errorArticle.put("error", "Failed to fetch news: " + e.getMessage());
            newsArticles.add(errorArticle);
        }
        
        log.info("Returning {} news articles for {}", newsArticles.size(), symbol);
        return newsArticles;
    }
    
    private boolean containsPositiveKeywords(String text) {
        String[] positiveKeywords = {
            "buy", "bullish", "upgrade", "growth", "profit", "gain", "positive", "beat", "exceed", 
            "outperform", "up", "higher", "rising", "surge", "rally", "strong", "boom", "success",
            "opportunity", "recommend", "upside", "optimistic", "promising", "innovation"
        };
        
        for (String keyword : positiveKeywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean containsNegativeKeywords(String text) {
        String[] negativeKeywords = {
            "sell", "bearish", "downgrade", "decline", "loss", "negative", "miss", "below", 
            "underperform", "down", "lower", "falling", "drop", "crash", "weak", "bust", "failure",
            "risk", "avoid", "downside", "pessimistic", "concerning", "disappointing", "investigation"
        };
        
        for (String keyword : negativeKeywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
}
