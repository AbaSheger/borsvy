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
import org.springframework.web.client.HttpClientErrorException;
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
import java.util.Random;

@Component
public class PolygonClient {
    private static final Logger logger = LoggerFactory.getLogger(PolygonClient.class);
    private final String apiKey;
    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final Random random = new Random();
    
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
            
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    
                    if (root.has("error")) {
                        logger.error("Polygon.io API error: {}", root.path("error").asText());
                        return generateMockData(symbol, startDate, endDate, interval);
                    }
                    
                    JsonNode results = root.path("results");
                    
                    if (results.isArray()) {
                        List<StockPrice> prices = new ArrayList<>();
                        for (JsonNode result : results) {
                            StockPrice price = new StockPrice();
                            price.setSymbol(symbol);
                            price.setTimestamp(LocalDateTime.ofEpochSecond(result.path("t").asLong() / 1000, 0, ZoneOffset.UTC));
                            price.setPrice(result.path("c").asDouble());
                            price.setVolume(result.path("v").asLong());
                            prices.add(price);
                        }
                        return prices;
                    }
                }
            } catch (HttpClientErrorException.TooManyRequests e) {
                logger.warn("Rate limit exceeded for Polygon.io API, using mock data");
                return generateMockData(symbol, startDate, endDate, interval);
            } catch (Exception e) {
                logger.error("Error fetching data from Polygon.io: {}", e.getMessage());
                return generateMockData(symbol, startDate, endDate, interval);
            }
            
            return generateMockData(symbol, startDate, endDate, interval);
            
        } catch (Exception e) {
            logger.error("Error in getHistoricalData: {}", e.getMessage(), e);
            return generateMockData(symbol, LocalDateTime.now().minusDays(1), LocalDateTime.now(), interval);
        }
    }

    private List<StockPrice> generateMockData(String symbol, LocalDateTime startDate, LocalDateTime endDate, String interval) {
        List<StockPrice> mockPrices = new ArrayList<>();
        LocalDateTime currentDate = startDate;
        double basePrice = 100.0; // Base price for mock data
        double currentPrice = basePrice;
        
        while (currentDate.isBefore(endDate) || currentDate.isEqual(endDate)) {
            StockPrice price = new StockPrice();
            price.setSymbol(symbol);
            price.setTimestamp(currentDate);
            
            // Add some random variation to the price
            double change = (random.nextDouble() - 0.5) * 2.0; // Random change between -1 and 1
            currentPrice = currentPrice + change;
            if (currentPrice < 1) currentPrice = basePrice; // Prevent negative prices
            
            price.setPrice(currentPrice);
            price.setVolume(random.nextLong(1000000, 10000000)); // Random volume
            mockPrices.add(price);
            
            // Increment the date based on interval
            switch (interval.toLowerCase()) {
                case "1d":
                    currentDate = currentDate.plusMinutes(5);
                    break;
                case "1w":
                    currentDate = currentDate.plusHours(1);
                    break;
                case "1m":
                    currentDate = currentDate.plusDays(1);
                    break;
                default:
                    currentDate = currentDate.plusDays(1);
            }
        }
        
        return mockPrices;
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
                return endDate.minusDays(1);
        }
    }

    private String getPolygonInterval(String interval) {
        switch (interval != null ? interval.toLowerCase() : "1d") {
            case "1d":
                return "1/minute";  // For intraday data
            case "1w":
                return "1/hour";    // For weekly data
            case "1m":
            case "3m":
            case "6m":
            case "1y":
            case "5y":
                return "1/day";     // For longer periods
            default:
                return "1/day";
        }
    }
} 