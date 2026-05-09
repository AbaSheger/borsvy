package com.borsvy.client;

import com.borsvy.model.Quote;
import com.borsvy.model.CompanyProfile2;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class FinnhubClient {
    private static final Logger logger = LoggerFactory.getLogger(FinnhubClient.class);
    private final String apiKey;
    private final String baseUrl = "https://finnhub.io/api/v1";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public FinnhubClient(@Value("${finnhub.api.key}") String apiKey, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public Quote getQuote(String symbol) {
        try {
            String url = String.format("%s/quote?symbol=%s&token=%s", baseUrl, symbol, apiKey);
            ResponseEntity<Quote> response = restTemplate.getForEntity(url, Quote.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                logger.error("Failed to get quote for symbol {}: {}", symbol, response.getStatusCode());
                throw new RestClientException("Failed to get quote data from Finnhub API");
            }
        } catch (RestClientException e) {
            logger.error("Error fetching quote for symbol {}: {}", symbol, e.getMessage());
            throw e;
        }
    }

    public CompanyProfile2 getCompanyProfile2(String symbol) {
        try {
            String url = String.format("%s/stock/profile2?symbol=%s&token=%s", baseUrl, symbol, apiKey);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            logger.debug("Raw Finnhub CompanyProfile2 response for {}: {}", symbol, response.getBody());
            return objectMapper.readValue(response.getBody(), CompanyProfile2.class);
        } catch (Exception e) {
            logger.error("Error fetching company profile from Finnhub: {}", e.getMessage());
            return null;
        }
    }

    /** Returns {"52WeekHigh": x, "52WeekLow": x} or empty map on failure. */
    public Map<String, Double> getBasicMetrics(String symbol) {
        try {
            String url = String.format("%s/stock/metric?symbol=%s&metric=all&token=%s", baseUrl, symbol, apiKey);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) return Map.of();
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response.getBody());
            com.fasterxml.jackson.databind.JsonNode metric = root.path("metric");
            if (metric.isMissingNode()) return Map.of();
            Map<String, Double> result = new java.util.HashMap<>();
            if (!metric.path("52WeekHigh").isMissingNode()) result.put("52WeekHigh", metric.path("52WeekHigh").asDouble(0));
            if (!metric.path("52WeekLow").isMissingNode())  result.put("52WeekLow",  metric.path("52WeekLow").asDouble(0));
            if (!metric.path("peBasicExclExtraTTM").isMissingNode()) result.put("pe", metric.path("peBasicExclExtraTTM").asDouble(0));
            return result;
        } catch (Exception e) {
            logger.warn("Error fetching metrics for {}: {}", symbol, e.getMessage());
            return Map.of();
        }
    }

    public List<Map<String, String>> searchSymbols(String query) {
        try {
            String url = String.format("%s/search?q=%s&token=%s", baseUrl,
                    java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8), apiKey);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) return List.of();

            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response.getBody());
            com.fasterxml.jackson.databind.JsonNode results = root.get("result");
            if (results == null || !results.isArray()) return List.of();

            List<Map<String, String>> items = new java.util.ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode node : results) {
                String symbol = node.path("symbol").asText("");
                String description = node.path("description").asText("");
                String type = node.path("type").asText("Stock");
                // Skip non-US exchanges and OTC pink sheets to keep results clean
                if (symbol.contains(".") || symbol.contains("-")) continue;
                items.add(Map.of("symbol", symbol, "name", description, "type", type));
                if (items.size() >= 10) break;
            }
            return items;
        } catch (Exception e) {
            logger.error("Error searching Finnhub symbols for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }
} 