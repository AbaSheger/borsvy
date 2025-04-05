package com.borsvy.client;

import com.borsvy.model.Quote;
import com.borsvy.model.CompanyProfile2;
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
} 