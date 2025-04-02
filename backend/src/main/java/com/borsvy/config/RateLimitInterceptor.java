package com.borsvy.config;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class RateLimitInterceptor implements ClientHttpRequestInterceptor {

    private final RetryTemplate retryTemplate;
    private final Random random = new Random();
    private final ConcurrentHashMap<String, Long> hostLastRequestTime = new ConcurrentHashMap<>();
    
    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:124.0) Gecko/20100101 Firefox/124.0",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
            "Mozilla/5.0 (iPad; CPU OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    };
    
    private static final String[] ACCEPT_LANGUAGE = {
            "en-US,en;q=0.9",
            "en-GB,en;q=0.9",
            "en;q=0.8,fr;q=0.6",
            "en-US;q=0.8,en;q=0.7"
    };

    public RateLimitInterceptor(RetryTemplate retryTemplate) {
        this.retryTemplate = retryTemplate;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        // Calculate time since last request to the same host
        String host = request.getURI().getHost();
        Long lastRequestTime = hostLastRequestTime.get(host);
        long now = System.currentTimeMillis();
        
        // Add a minimum delay between requests to the same host (12 seconds for Alpha Vantage free tier)
        if (lastRequestTime != null) {
            long timeSinceLastRequest = now - lastRequestTime;
            long minDelay = 12000; // Alpha Vantage free tier limit
            
            if (timeSinceLastRequest < minDelay) {
                try {
                    TimeUnit.MILLISECONDS.sleep(minDelay - timeSinceLastRequest);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        // Add browser-like headers to avoid detection
        request.getHeaders().set("User-Agent", USER_AGENTS[random.nextInt(USER_AGENTS.length)]);
        request.getHeaders().set("Accept-Language", ACCEPT_LANGUAGE[random.nextInt(ACCEPT_LANGUAGE.length)]);
        request.getHeaders().set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        request.getHeaders().set("Referer", "https://finance.yahoo.com/");
        request.getHeaders().set("Sec-Fetch-Dest", "document");
        request.getHeaders().set("Sec-Fetch-Mode", "navigate");
        request.getHeaders().set("Sec-Fetch-Site", "same-origin");
        request.getHeaders().set("Sec-Fetch-User", "?1");
        request.getHeaders().set("Upgrade-Insecure-Requests", "1");
        request.getHeaders().set("Connection", "keep-alive");
        
        // Update last request time before making the request
        hostLastRequestTime.put(host, System.currentTimeMillis());
        
        return retryTemplate.execute(context -> {
            try {
                ClientHttpResponse response = execution.execute(request, body);
                
                // Check if it's a rate limit error (HTTP 429)
                if (response.getStatusCode().value() == 429) {
                    System.out.println("Rate limit hit for API request: " + request.getURI());
                    
                    // Sleep for a longer time (30 seconds) before retrying
                    try {
                        System.out.println("Waiting 30 seconds before retry...");
                        Thread.sleep(30000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    throw new RestTemplateConfig.RateLimitException("Too many requests to Alpha Vantage API");
                }
                
                return response;
            } catch (HttpClientErrorException e) {
                // Check if it's a rate limit error (HTTP 429)
                if (e.getRawStatusCode() == 429) {
                    System.out.println("Rate limit hit for API request: " + request.getURI());
                    
                    // Sleep for a longer time (30 seconds) before retrying
                    try {
                        System.out.println("Waiting 30 seconds before retry...");
                        Thread.sleep(30000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    
                    throw new RestTemplateConfig.RateLimitException("Too many requests to Alpha Vantage API");
                }
                // For other HTTP errors, rethrow the original exception
                throw e;
            }
        });
    }
}