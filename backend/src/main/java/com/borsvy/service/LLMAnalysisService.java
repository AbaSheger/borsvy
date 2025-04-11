package com.borsvy.service;

import com.borsvy.model.Stock;
import com.borsvy.model.StockAnalysis;
import com.borsvy.model.StockPrice;
import com.borsvy.client.PolygonClient;
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
import org.springframework.context.annotation.Lazy;

@Slf4j
@Service
public class LLMAnalysisService implements NewsAnalysisService {

    private final RestTemplate restTemplate;
    private StockService stockService; // Changed from final to allow setter injection
    private final ObjectMapper objectMapper;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final PolygonClient polygonClient;
    
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    
    @Value("${groq.api.key:fallback}")
    private String apiKey;

    @Value("${groq.model.id:llama3-70b-8192}")
    private String modelId;
    
    @Autowired
    public LLMAnalysisService(RestTemplate restTemplate, ObjectMapper objectMapper,
                             TechnicalIndicatorService technicalIndicatorService, 
                             PolygonClient polygonClient, 
                             @Lazy StockService stockService) {  // Added @Lazy annotation here
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.technicalIndicatorService = technicalIndicatorService;
        this.polygonClient = polygonClient;
        this.stockService = stockService;
        log.info("LLMAnalysisService initialized - using Groq API: {}", apiKey != null && !apiKey.equals("fallback"));
    }
    
    public Map<String, Object> generateAnalysis(String symbol) {
        log.debug("Starting analysis for symbol: {}", symbol);
        
        try {
            // Get stock data
            Stock stock = stockService.getStockBySymbol(symbol)
                .orElseThrow(() -> new RuntimeException("Stock not found"));
            
            // Get price history from the PolygonClient
            List<StockPrice> priceHistory = polygonClient.getHistoricalData(symbol, "1day");
            
            // Calculate technical indicators
            Map<String, Object> technicalData = technicalIndicatorService.generateTechnicalAnalysis(priceHistory, symbol);
            
            // Add technical indicator data to the stock for backward compatibility
            if (technicalData.containsKey("rsi")) {
                stock.setRsi(((Number) technicalData.get("rsi")).doubleValue());
            }
            if (technicalData.containsKey("sma20")) {
                stock.setSma20(((Number) technicalData.get("sma20")).doubleValue());
            }
            if (technicalData.containsKey("sma50")) {
                stock.setSma50(((Number) technicalData.get("sma50")).doubleValue());
            }
            
            log.info("Analyzing {} - Price: ${}, Change: {}%, Volume: {}", 
                symbol, stock.getPrice(), stock.getChangePercent(), stock.getVolume());
                
            // Try using Groq API if configured
            String sentiment;
            double confidence;
            
            if (!apiKey.equals("fallback")) {
                try {
                    // Try using the API
                    log.debug("Attempting to use Groq API for analysis");
                    Map<String, Object> apiResult = callGroqApi(stock);
                    sentiment = (String) apiResult.get("sentiment");
                    confidence = (Double) apiResult.get("confidence");
                    log.info("Successfully got sentiment from Groq API: {} with confidence {}", sentiment, confidence);
                } catch (Exception e) {
                    // Log the error but continue with local analysis
                    log.warn("Groq API call failed: {}. Using local analysis instead.", e.getMessage());
                    sentiment = determineLocalSentiment(stock);
                    confidence = calculateDynamicConfidence(stock, sentiment);
                }
            } else {
                // No API key, use local analysis
                log.debug("No Groq API key configured, using local sentiment analysis");
                sentiment = determineLocalSentiment(stock);
                confidence = calculateDynamicConfidence(stock, sentiment);
            }
            
            // Generate detailed analysis text
            String analysis = generateAnalysisText(stock, sentiment, confidence);
            
            // Create response
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("sentiment", sentiment.toLowerCase());
            responseMap.put("confidence", confidence);
            responseMap.put("analysis", analysis);
            
            log.info("Analysis for {} complete: {} sentiment with {:.1f}% confidence", 
                symbol, sentiment, confidence * 100);
            
            return responseMap;
            
        } catch (Exception e) {
            log.error("Error generating analysis for {}: {}", symbol, e.getMessage(), e);
            throw new RuntimeException("Failed to generate analysis: " + e.getMessage());
        }
    }
    
    /**
     * Determines sentiment locally based on stock data when API is unavailable
     */
    private String determineLocalSentiment(Stock stock) {
        double changePercent = stock.getChangePercent();
        double macd = stock.getMacd();
        double rsi = stock.getRsi();
        
        // Count positive and negative signals
        int positiveSignals = 0;
        int negativeSignals = 0;
        
        // Price movement signals
        if (changePercent > 3.0) positiveSignals += 2;
        else if (changePercent > 1.0) positiveSignals += 1;
        else if (changePercent < -3.0) negativeSignals += 2;
        else if (changePercent < -1.0) negativeSignals += 1;
        
        // MACD signals
        if (macd > 0.5) positiveSignals += 1;
        else if (macd < -0.5) negativeSignals += 1;
        
        // RSI signals
        if (rsi > 70) negativeSignals += 1; // Overbought
        else if (rsi < 30) positiveSignals += 1; // Oversold (buying opportunity)
        
        // Moving average signals
        if (stock.getPrice() > stock.getSma20()) positiveSignals += 1;
        else if (stock.getPrice() < stock.getSma20()) negativeSignals += 1;
        
        // Determine sentiment based on signals
        if (positiveSignals > negativeSignals + 1) return "POSITIVE";
        else if (negativeSignals > positiveSignals + 1) return "NEGATIVE";
        else return "NEUTRAL";
    }
    
    /**
     * Calculates a dynamic, varied confidence score based on stock data
     */
    private double calculateDynamicConfidence(Stock stock, String sentiment) {
        double baseConfidence = 0.5; // Start with neutral confidence
        double changePercent = Math.abs(stock.getChangePercent());
        
        // Factor 1: Price movement strength - more extreme changes = higher confidence
        double priceFactor = Math.min(changePercent / 15.0, 0.25); // Max 25% contribution
        
        // Factor 2: Technical indicator alignment with sentiment
        double technicalFactor = 0;
        if (sentiment.equals("POSITIVE")) {
            if (stock.getMacd() > 0) technicalFactor += 0.05;
            if (stock.getRsi() < 40) technicalFactor += 0.05;
            if (stock.getPrice() > stock.getSma20()) technicalFactor += 0.05;
            if (stock.getSma20() > stock.getSma50()) technicalFactor += 0.05;
        } else if (sentiment.equals("NEGATIVE")) {
            if (stock.getMacd() < 0) technicalFactor += 0.05;
            if (stock.getRsi() > 60) technicalFactor += 0.05;
            if (stock.getPrice() < stock.getSma20()) technicalFactor += 0.05;
            if (stock.getSma20() < stock.getSma50()) technicalFactor += 0.05;
        }
        
        // Factor 3: Volume significance - higher volume = stronger signal
        double volumeFactor = 0;
        if (stock.getVolume() > 1000000) volumeFactor = 0.15;
        else if (stock.getVolume() > 500000) volumeFactor = 0.10;
        else if (stock.getVolume() > 100000) volumeFactor = 0.05;
        
        // Add a small random factor for variety (±5%)
        double randomFactor = (Math.random() * 0.1) - 0.05;
        
        // Calculate total confidence
        double confidence = baseConfidence + priceFactor + technicalFactor + volumeFactor + randomFactor;
        
        // Ensure confidence stays in reasonable bounds
        confidence = Math.max(0.35, Math.min(0.95, confidence));
        
        return confidence;
    }
    
    /**
     * Calls the Groq API to get sentiment analysis
     */
    private Map<String, Object> callGroqApi(Stock stock) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            
            // Create a proper prompt for the model
            String prompt = createGroqPrompt(stock);
            
            // Log the actual prompt for debugging
            log.info("Using prompt for Groq API: {}", prompt);
            log.info("Using model: {} with API URL: {}", modelId, GROQ_API_URL);
            
            // Create the message structure that Groq expects
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);
            
            List<Map<String, Object>> messages = new java.util.ArrayList<>();
            messages.add(message);
            
            // Create the full request
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelId);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.2); // Low temperature for more consistent results
            requestBody.put("max_tokens", 250); // Limit response size
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            
            log.debug("Sending request to Groq API");
            ResponseEntity<Map> response = restTemplate.exchange(
                GROQ_API_URL,
                HttpMethod.POST,
                requestEntity,
                Map.class
            );
            
            // Process the response
            Map<String, Object> responseBody = response.getBody();
            
            // Log the full response for debugging
            log.info("Received Groq API response with status code: {}", response.getStatusCode());
            try {
                log.debug("Full response body: {}", objectMapper.writeValueAsString(responseBody));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.warn("Could not serialize response body for logging: {}", e.getMessage());
            }
            
            // Extract sentiment and confidence from the response using Groq's structure
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> messageResponse = (Map<String, Object>) choice.get("message");
                
                if (messageResponse != null && messageResponse.containsKey("content")) {
                    String content = (String) messageResponse.get("content");
                    log.info("Successfully extracted content from Groq API: [{}]", content);
                    
                    // Parse the content looking for sentiment and confidence
                    Map<String, Object> sentimentResult = extractSentimentFromText(content);
                    
                    // If we successfully parsed the sentiment and confidence, return it
                    if (sentimentResult.get("sentiment") != null && sentimentResult.get("confidence") != null) {
                        log.info("Successfully extracted sentiment: {} with confidence {}", 
                                sentimentResult.get("sentiment"), sentimentResult.get("confidence"));
                        return sentimentResult;
                    }
                }
            }
            
            // If we couldn't parse the response properly, log it and use fallback
            log.warn("Could not extract sentiment from Groq API response, using local analysis");
            String sentiment = determineLocalSentiment(stock);
            double confidence = calculateDynamicConfidence(stock, sentiment);
            
            Map<String, Object> result = new HashMap<>();
            result.put("sentiment", sentiment);
            result.put("confidence", confidence);
            return result;
            
        } catch (Exception e) {
            log.error("Error calling Groq API: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Creates a prompt for the Groq LLM to analyze stock sentiment
     * Optimized for the llama-3.3-70b-versatile model
     */
    private String createGroqPrompt(Stock stock) {
        StringBuilder prompt = new StringBuilder();
        
        // Better formatting for llama-3.3-70b-versatile
        prompt.append("You are a financial analyst assistant specialized in stock market sentiment analysis. ");
        prompt.append("Analyze the sentiment for this stock based on the following market data.\n\n");
        
        // The specific instruction with format requirements
        prompt.append("IMPORTANT: Your response must follow this exact format:\n");
        prompt.append("SENTIMENT:[SENTIMENT] CONFIDENCE:[CONFIDENCE]\n");
        prompt.append("where [SENTIMENT] is exactly one of: POSITIVE, NEGATIVE, or NEUTRAL\n");
        prompt.append("and [CONFIDENCE] is a number between 0 and 1 representing your confidence level.\n\n");
        
        // Sample format to show the model
        prompt.append("Example correct response: SENTIMENT:POSITIVE CONFIDENCE:0.85\n\n");
        
        // Stock data with clear structure
        prompt.append("==== STOCK DATA ====\n");
        prompt.append("Symbol: ").append(stock.getSymbol()).append("\n");
        prompt.append("Current Price: $").append(String.format("%.2f", stock.getPrice())).append("\n");
        prompt.append("Percent Change Today: ").append(String.format("%.2f", stock.getChangePercent())).append("%\n");
        prompt.append("Trading Volume: ").append(formatNumber(stock.getVolume())).append("\n");
        
        // Add technical indicators with explanations when available
        if (stock.getRsi() > 0) {
            prompt.append("RSI: ").append(String.format("%.2f", stock.getRsi()))
                  .append(" (>70 overbought, <30 oversold)\n");
        }
        
        if (stock.getMacd() != 0) {
            prompt.append("MACD: ").append(String.format("%.2f", stock.getMacd()))
                  .append(" (positive: bullish, negative: bearish)\n");
        }
        
        if (stock.getSma20() > 0) {
            double priceTo20SMA = ((stock.getPrice() / stock.getSma20()) - 1) * 100;
            prompt.append("20-day SMA: ").append(String.format("%.2f", stock.getSma20()))
                  .append(" (price ").append(priceTo20SMA > 0 ? "above" : "below").append(" by ")
                  .append(String.format("%.2f%%", Math.abs(priceTo20SMA))).append(")\n");
        }
        
        if (stock.getSma50() > 0) {
            double priceTo50SMA = ((stock.getPrice() / stock.getSma50()) - 1) * 100;
            prompt.append("50-day SMA: ").append(String.format("%.2f", stock.getSma50()))
                  .append(" (price ").append(priceTo50SMA > 0 ? "above" : "below").append(" by ")
                  .append(String.format("%.2f%%", Math.abs(priceTo50SMA))).append(")\n");
        }
        
        // Final instruction to ensure proper response format
        prompt.append("\nBased on this data, provide ONLY the sentiment (POSITIVE, NEGATIVE, or NEUTRAL) and confidence score. Nothing else.");
        
        return prompt.toString();
    }
    
    /**
     * Format large numbers for better readability in prompts
     */
    private String formatNumber(long number) {
        if (number >= 1_000_000_000) {
            return String.format("%.2f billion", number / 1_000_000_000.0);
        } else if (number >= 1_000_000) {
            return String.format("%.2f million", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.2f thousand", number / 1_000.0);
        }
        return String.format("%d", number);
    }
    
    /**
     * Extracts sentiment and confidence from LLM text response
     */
    private Map<String, Object> extractSentimentFromText(String text) {
        Map<String, Object> result = new HashMap<>();
        String sentiment = "NEUTRAL";
        double confidence = 0.5;
        
        // Default values in case extraction fails
        result.put("sentiment", sentiment);
        result.put("confidence", confidence);
        
        try {
            // Look for sentiment pattern
            if (text.contains("SENTIMENT:") && text.contains("CONFIDENCE:")) {
                // Extract sentiment
                int sentimentStart = text.indexOf("SENTIMENT:") + 10;
                int sentimentEnd = text.indexOf(" ", sentimentStart);
                if (sentimentEnd == -1) {
                    // Try looking for newline if space not found
                    sentimentEnd = text.indexOf("\n", sentimentStart);
                }
                
                if (sentimentEnd != -1) {
                    sentiment = text.substring(sentimentStart, sentimentEnd).trim();
                    result.put("sentiment", sentiment);
                }
                
                // Extract confidence
                int confidenceStart = text.indexOf("CONFIDENCE:") + 11;
                int confidenceEnd = text.indexOf(" ", confidenceStart);
                if (confidenceEnd == -1) {
                    // Try looking for newline if space not found
                    confidenceEnd = text.indexOf("\n", confidenceStart);
                }
                
                if (confidenceEnd == -1) {
                    // If still not found, take rest of string
                    confidenceEnd = text.length();
                }
                
                if (confidenceEnd != -1) {
                    String confidenceStr = text.substring(confidenceStart, confidenceEnd).trim();
                    try {
                        confidence = Double.parseDouble(confidenceStr);
                        result.put("confidence", confidence);
                    } catch (NumberFormatException e) {
                        log.warn("Could not parse confidence value: {}", confidenceStr);
                    }
                }
            } else {
                // Simple sentiment extraction if format doesn't match
                if (text.toLowerCase().contains("positive")) {
                    sentiment = "POSITIVE";
                    confidence = 0.7;
                } else if (text.toLowerCase().contains("negative")) {
                    sentiment = "NEGATIVE";
                    confidence = 0.7;
                }
                
                result.put("sentiment", sentiment);
                result.put("confidence", confidence);
            }
        } catch (Exception e) {
            log.warn("Error extracting sentiment from text: {}", e.getMessage());
        }
        
        return result;
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
    
    /**
     * Analyzes sentiment of news articles using LLM
     * @param symbol The stock symbol
     * @param newsArticles List of news articles to analyze
     * @return A map containing sentiment analysis results
     */
    public Map<String, Object> analyzeNewsSentiment(String symbol, List<com.borsvy.model.NewsArticle> newsArticles) {
        log.info("Analyzing news sentiment for {} with {} articles", symbol, newsArticles.size());
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (newsArticles.isEmpty()) {
                log.warn("No news articles provided for sentiment analysis");
                result.put("sentiment", "neutral");
                result.put("confidence", 0.5);
                result.put("error", "No news articles available");
                return result;
            }
            
            // Create a proper prompt for the LLM
            String prompt = createNewsSentimentPrompt(symbol, newsArticles);
            
            // Try using Groq API if configured
            if (!apiKey.equals("fallback")) {
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("Authorization", "Bearer " + apiKey);
                    
                    // Create the message structure for Groq
                    Map<String, Object> message = new HashMap<>();
                    message.put("role", "user");
                    message.put("content", prompt);
                    
                    List<Map<String, Object>> messages = new java.util.ArrayList<>();
                    messages.add(message);
                    
                    // Create the full request
                    Map<String, Object> requestBody = new HashMap<>();
                    requestBody.put("model", modelId);
                    requestBody.put("messages", messages);
                    requestBody.put("temperature", 0.2);
                    requestBody.put("max_tokens", 250);
                    
                    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
                    
                    log.debug("Sending news sentiment analysis request to Groq API");
                    ResponseEntity<Map> response = restTemplate.exchange(
                        GROQ_API_URL,
                        HttpMethod.POST,
                        requestEntity,
                        Map.class
                    );
                    
                    // Process the response
                    Map<String, Object> responseBody = response.getBody();
                    
                    // Extract sentiment from the response
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> choice = choices.get(0);
                        Map<String, Object> messageResponse = (Map<String, Object>) choice.get("message");
                        
                        if (messageResponse != null && messageResponse.containsKey("content")) {
                            String content = (String) messageResponse.get("content");
                            log.debug("LLM response for news sentiment: {}", content);
                            
                            // Parse the sentiment from the response
                            Map<String, Object> sentimentResult = extractSentimentFromText(content);
                            
                            String sentiment = (String) sentimentResult.get("sentiment");
                            double confidence = (Double) sentimentResult.get("confidence");
                            
                            // Create a more detailed response
                            result.put("sentiment", sentiment.toLowerCase());
                            result.put("confidence", confidence);
                            result.put("analysis", generateNewsSentimentSummary(symbol, newsArticles, sentiment, confidence));
                            
                            // Add article breakdown if available
                            if (content.contains("ARTICLE BREAKDOWN:")) {
                                String breakdown = extractArticleBreakdown(content);
                                if (breakdown != null) {
                                    result.put("articleBreakdown", breakdown);
                                }
                            }
                            
                            log.info("Successfully analyzed news sentiment for {}: {} with {}% confidence", 
                                    symbol, sentiment, String.format("%.1f", confidence * 100));
                            
                            return result;
                        }
                    }
                    
                    log.warn("Could not extract sentiment from Groq API response");
                } catch (Exception e) {
                    log.error("Error analyzing news sentiment with Groq API: {}", e.getMessage());
                }
            }
            
            // Fallback to simple sentiment analysis
            log.warn("Falling back to simple news sentiment analysis");
            String sentiment = calculateSimpleNewsSentiment(newsArticles);
            double confidence = 0.6; // Moderate confidence for the simple approach
            
            result.put("sentiment", sentiment.toLowerCase());
            result.put("confidence", confidence);
            result.put("analysis", generateNewsSentimentSummary(symbol, newsArticles, sentiment, confidence));
            
            return result;
            
        } catch (Exception e) {
            log.error("Error analyzing news sentiment: {}", e.getMessage());
            result.put("sentiment", "neutral");
            result.put("confidence", 0.5);
            result.put("error", "Failed to analyze news sentiment: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Creates a prompt for the LLM to analyze news article sentiments
     */
    private String createNewsSentimentPrompt(String symbol, List<com.borsvy.model.NewsArticle> newsArticles) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are a financial news analyst specialized in sentiment analysis. ");
        prompt.append("Analyze the sentiment in these news headlines about ").append(symbol).append(".\n\n");
        
        // The specific instruction with format requirements
        prompt.append("IMPORTANT: Your response must follow this exact format:\n");
        prompt.append("SENTIMENT:[SENTIMENT] CONFIDENCE:[CONFIDENCE]\n");
        prompt.append("where [SENTIMENT] is exactly one of: POSITIVE, NEGATIVE, or NEUTRAL\n");
        prompt.append("and [CONFIDENCE] is a number between 0 and 1 representing your confidence level.\n\n");
        
        prompt.append("Then include: ARTICLE BREAKDOWN: followed by a brief sentiment analysis of the most important headlines.\n\n");
        
        // List all headlines
        prompt.append("==== NEWS HEADLINES ====\n");
        int count = 1;
        for (com.borsvy.model.NewsArticle article : newsArticles) {
            prompt.append(count).append(". ").append(article.getTitle()).append(" [").append(article.getPublishedDate()).append("]\n");
            count++;
            if (count > 10) break; // Limit to 10 headlines
        }
        
        prompt.append("\nBased on these headlines, determine if the overall news sentiment for ");
        prompt.append(symbol).append(" is POSITIVE, NEGATIVE, or NEUTRAL. ");
        prompt.append("Focus on how these headlines might impact stock price.\n\n");
        prompt.append("First provide the SENTIMENT and CONFIDENCE, then the article breakdown.");
        
        return prompt.toString();
    }
    
    /**
     * Extract article breakdown analysis from LLM response
     */
    private String extractArticleBreakdown(String content) {
        try {
            int breakdownStart = content.indexOf("ARTICLE BREAKDOWN:");
            if (breakdownStart != -1) {
                return content.substring(breakdownStart + "ARTICLE BREAKDOWN:".length()).trim();
            }
        } catch (Exception e) {
            log.warn("Error extracting article breakdown: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Simple keyword-based sentiment analysis for news articles (fallback method)
     */
    private String calculateSimpleNewsSentiment(List<com.borsvy.model.NewsArticle> newsArticles) {
        int positiveScore = 0;
        int negativeScore = 0;
        
        // Keywords that typically indicate positive/negative sentiment in financial news
        String[] positiveWords = {"surge", "gain", "rise", "jump", "high", "growth", "profit", "up", "boost", 
                                "soar", "bullish", "beat", "exceed", "positive", "strong", "success"};
        
        String[] negativeWords = {"drop", "fall", "decline", "plunge", "low", "down", "loss", "miss", "weak", 
                                "bearish", "sink", "crash", "tumble", "negative", "failed", "concern"};
        
        // Count occurrences of positive and negative keywords
        for (com.borsvy.model.NewsArticle article : newsArticles) {
            String title = article.getTitle().toLowerCase();
            
            for (String word : positiveWords) {
                if (title.contains(word.toLowerCase())) {
                    positiveScore++;
                }
            }
            
            for (String word : negativeWords) {
                if (title.contains(word.toLowerCase())) {
                    negativeScore++;
                }
            }
        }
        
        // Determine sentiment based on keyword counts
        if (positiveScore > negativeScore + 2) {
            return "POSITIVE";
        } else if (negativeScore > positiveScore + 2) {
            return "NEGATIVE";
        } else {
            return "NEUTRAL";
        }
    }
    
    /**
     * Generate a summary analysis of news sentiment
     */
    private String generateNewsSentimentSummary(String symbol, List<com.borsvy.model.NewsArticle> newsArticles, 
                                              String sentiment, double confidence) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("News Sentiment Analysis for ").append(symbol).append(":\n\n");
        summary.append("Based on analysis of ").append(newsArticles.size()).append(" recent news articles, ");
        summary.append("the overall sentiment is ").append(sentiment).append(" with ");
        summary.append(String.format("%.1f", confidence * 100)).append("% confidence.\n\n");
        
        // Add some general observations based on the sentiment
        if (sentiment.equals("POSITIVE")) {
            summary.append("The headlines suggest generally positive developments for ").append(symbol);
            summary.append(", which may have a favorable impact on the stock price.");
        } else if (sentiment.equals("NEGATIVE")) {
            summary.append("The headlines suggest concerning developments for ").append(symbol);
            summary.append(", which may have a negative impact on the stock price.");
        } else {
            summary.append("The headlines show mixed or balanced news for ").append(symbol);
            summary.append(", suggesting no clear directional impact on the stock price.");
        }
        
        return summary.toString();
    }
}