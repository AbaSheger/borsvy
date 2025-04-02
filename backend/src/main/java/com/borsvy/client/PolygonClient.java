package com.borsvy.client;

import com.borsvy.model.StockPrice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Component
public class PolygonClient {
    private static final Logger logger = LoggerFactory.getLogger(PolygonClient.class);
    private final String apiKey;
    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    // Map of stock symbols to their exchanges
    private static final Map<String, String> EXCHANGE_PREFIXES = new HashMap<>();
    static {
        // NASDAQ stocks
        EXCHANGE_PREFIXES.put("AAPL", "NASDAQ");
        EXCHANGE_PREFIXES.put("MSFT", "NASDAQ");
        EXCHANGE_PREFIXES.put("GOOGL", "NASDAQ");
        EXCHANGE_PREFIXES.put("META", "NASDAQ");
        EXCHANGE_PREFIXES.put("NVDA", "NASDAQ");
        EXCHANGE_PREFIXES.put("TSLA", "NASDAQ");
        // NYSE stocks
        EXCHANGE_PREFIXES.put("JPM", "NYSE");
        EXCHANGE_PREFIXES.put("V", "NYSE");
        EXCHANGE_PREFIXES.put("WMT", "NYSE");
    }

    @Autowired
    public PolygonClient(@Value("${polygon.api.key}") String apiKey,
                        @Value("${polygon.api.url}") String baseUrl,
                        RestTemplate restTemplate,
                        ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public List<StockPrice> getHistoricalData(String symbol, String interval) {
        try {
            // Calculate date range based on interval
            LocalDateTime endDate = LocalDateTime.now();
            LocalDateTime startDate = calculateStartDate(endDate, interval);
            
            // Format dates for Polygon.io API
            String fromDate = startDate.format(DATE_FORMATTER);
            String toDate = endDate.format(DATE_FORMATTER);
            
            // Construct the URL with proper formatting
            String url = String.format("%s/aggs/ticker/%s/range/%s/%s/%s?apiKey=%s&limit=50000&sort=asc",
                    baseUrl,
                    symbol,
                    getPolygonInterval(interval),
                    fromDate,
                    toDate,
                    apiKey);

            logger.debug("Making request to Polygon.io: {}", url);
            
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            logger.debug("Polygon.io response status: {}", response.getStatusCode());
            logger.debug("Polygon.io response headers: {}", response.getHeaders());
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                logger.debug("Polygon.io response body: {}", response.getBody());
                JsonNode root = objectMapper.readTree(response.getBody());
                
                // Check for error in response
                if (root.has("error")) {
                    logger.error("Polygon.io API error: {}", root.path("error").asText());
                    return new ArrayList<>();
                }
                
                // Check for results count
                if (root.has("resultsCount")) {
                    logger.debug("Polygon.io results count: {}", root.path("resultsCount").asInt());
                }
                
                JsonNode results = root.path("results");
                
                if (results.isArray()) {
                    List<StockPrice> prices = new ArrayList<>();
                    for (JsonNode result : results) {
                        StockPrice price = new StockPrice();
                        price.setSymbol(symbol); // Use original symbol without exchange prefix
                        price.setTimestamp(LocalDateTime.ofEpochSecond(result.path("t").asLong() / 1000, 0, ZoneOffset.UTC));
                        price.setPrice(result.path("c").asDouble());
                        price.setVolume(result.path("v").asLong());
                        prices.add(price);
                    }
                    logger.info("Successfully retrieved {} price points for {}", prices.size(), symbol);
                    return prices;
                } else {
                    logger.error("No results array in Polygon.io response for {}. Response: {}", symbol, response.getBody());
                }
            } else {
                logger.error("Failed to get historical data for symbol {}: {} - Response body: {}", 
                    symbol, response.getStatusCode(), response.getBody());
            }
            
            return new ArrayList<>();
            
        } catch (Exception e) {
            logger.error("Error fetching historical data for symbol {}: {}", symbol, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private LocalDateTime calculateStartDate(LocalDateTime endDate, String interval) {
        switch (interval != null ? interval.toLowerCase() : "1d") {
            case "1w":
                return endDate.minusWeeks(1);
            case "1m":
                return endDate.minusMonths(1);
            case "3m":
                return endDate.minusMonths(3);
            case "6m":
                return endDate.minusMonths(6);
            case "1y":
                return endDate.minusYears(1);
            case "5y":
                return endDate.minusYears(5);
            default: // 1d
                return endDate.minusDays(7);
        }
    }

    private String getPolygonInterval(String interval) {
        switch (interval != null ? interval.toLowerCase() : "1d") {
            case "1d":
                return "1/day";
            case "1w":
            case "1m":
            case "3m":
            case "6m":
            case "1y":
            case "5y":
                return "1/day";
            default:
                return "1/day";
        }
    }
} 