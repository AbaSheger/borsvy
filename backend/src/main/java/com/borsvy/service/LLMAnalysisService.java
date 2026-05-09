package com.borsvy.service;

import com.borsvy.model.Stock;
import com.borsvy.model.StockAnalysis;
import com.borsvy.model.StockDetails;
import com.borsvy.model.StockPrice;
import com.borsvy.model.NewsArticle;
import com.borsvy.client.TwelveDataClient;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayList;
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
    private final TwelveDataClient twelveDataClient;
    
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    
    @Value("${groq.api.key:fallback}")
    private String apiKey;

    @Value("${groq.model.id:llama3-70b-8192}")
    private String modelId;
    
    @Autowired
    public LLMAnalysisService(RestTemplate restTemplate, ObjectMapper objectMapper,
                             TechnicalIndicatorService technicalIndicatorService,
                             TwelveDataClient twelveDataClient,
                             @Lazy StockService stockService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.technicalIndicatorService = technicalIndicatorService;
        this.twelveDataClient = twelveDataClient;
        this.stockService = stockService;
    }
    
    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("LLMAnalysisService initialized - using Groq API: {}", apiKey != null && !apiKey.equals("fallback"));
    }
    
    public Map<String, Object> generateAnalysis(String symbol) {
        log.info("Starting analysis for symbol: {}", symbol);

        try {
            // Get stock data
            Stock stock = stockService.getStockBySymbol(symbol)
                .orElseThrow(() -> new RuntimeException("Stock not found"));

            // Get price history from Twelve Data
            List<StockPrice> priceHistory = twelveDataClient.getHistoricalData(symbol, "1m");

            // Calculate technical indicators
            Map<String, Object> technicalData = technicalIndicatorService.generateTechnicalAnalysis(priceHistory, symbol);

            // Add technical indicator data to the stock
            if (technicalData.containsKey("rsi")) {
                stock.setRsi(Double.parseDouble(technicalData.get("rsi").toString()));
            }
            if (technicalData.containsKey("sma20")) {
                stock.setSma20(Double.parseDouble(technicalData.get("sma20").toString()));
            }
            if (technicalData.containsKey("sma50")) {
                stock.setSma50(Double.parseDouble(technicalData.get("sma50").toString()));
            }
            if (technicalData.containsKey("macd")) {
                Map<String, Object> macdData = (Map<String, Object>) technicalData.get("macd");
                if (macdData != null && macdData.containsKey("macd")) {
                    stock.setMacd(Double.parseDouble(macdData.get("macd").toString()));
                }
            }

            // Fetch StockDetails for richer context (company name, sector, PE, beta, market cap)
            StockDetails details = null;
            try {
                details = stockService.getStockDetails(symbol);
            } catch (Exception e) {
                log.warn("Could not fetch StockDetails for {}: {}", symbol, e.getMessage());
            }

            // Fetch recent news headlines for context
            List<NewsArticle> newsArticles = new ArrayList<>();
            try {
                newsArticles = stockService.getStockNews(symbol, 5);
            } catch (Exception e) {
                log.warn("Could not fetch news for {}: {}", symbol, e.getMessage());
            }

            log.info("Analyzing {} - Price: ${}, Change: {}%, Volume: {}",
                symbol, stock.getPrice(), stock.getChangePercent(), stock.getVolume());

            // Create prompt requesting full JSON analysis
            String prompt = createGroqPrompt(stock, details, newsArticles);

            // Call Groq API
            Map<String, Object> response = callGroqApi(prompt);

            if (response == null || response.isEmpty()) {
                log.error("Failed to get response from Groq API");
                throw new RuntimeException("Failed to get response from Groq API");
            }

            // Parse JSON response
            String content = (String) response.get("content");
            Map<String, Object> result = parseGroqJsonResponse(content, stock);
            result.put("technical", technicalData);

            log.info("Analysis completed for {} - Sentiment: {}, Confidence: {}",
                symbol, result.get("sentiment"), result.get("confidence"));
            return result;

        } catch (Exception e) {
            log.error("Error generating analysis: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate analysis: " + e.getMessage());
        }
    }

    /**
     * Parses the Groq JSON response into a result map.
     * Handles JSON wrapped in markdown code blocks.
     */
    private Map<String, Object> parseGroqJsonResponse(String content, Stock stock) {
        Map<String, Object> result = new HashMap<>();
        try {
            // Strip markdown code fences if present
            String json = content.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
            }

            JsonNode node = objectMapper.readTree(json);

            String sentiment = node.has("sentiment") ? node.get("sentiment").asText("NEUTRAL").toUpperCase() : "NEUTRAL";
            if (!sentiment.equals("POSITIVE") && !sentiment.equals("NEGATIVE")) sentiment = "NEUTRAL";

            double confidence = node.has("confidence") ? node.get("confidence").asDouble(0.5) : 0.5;
            confidence = Math.max(0.1, Math.min(0.95, confidence));

            String summary = node.has("summary") ? node.get("summary").asText("") : "";
            String outlook = node.has("outlook") ? node.get("outlook").asText("") : "";

            List<String> bullishPoints = new ArrayList<>();
            if (node.has("bullishPoints") && node.get("bullishPoints").isArray()) {
                node.get("bullishPoints").forEach(n -> bullishPoints.add(n.asText()));
            }

            List<String> bearishRisks = new ArrayList<>();
            if (node.has("bearishRisks") && node.get("bearishRisks").isArray()) {
                node.get("bearishRisks").forEach(n -> bearishRisks.add(n.asText()));
            }

            result.put("sentiment", sentiment);
            result.put("confidence", confidence);
            result.put("summary", summary);
            result.put("outlook", outlook);
            result.put("bullishPoints", bullishPoints);
            result.put("bearishRisks", bearishRisks);
            // Keep "analysis" key for backward compat with AnalysisService
            result.put("analysis", summary);

        } catch (Exception e) {
            log.error("Failed to parse Groq JSON response, falling back to local analysis: {}", e.getMessage());
            // Fallback: local sentiment determination
            String sentiment = determineLocalSentiment(stock);
            double confidence = calculateDynamicConfidence(stock, sentiment);
            result.put("sentiment", sentiment);
            result.put("confidence", confidence);
            result.put("summary", "Analysis unavailable. Technical indicators suggest a " + sentiment.toLowerCase() + " outlook.");
            result.put("outlook", "Monitor the stock for clearer signals.");
            result.put("bullishPoints", new ArrayList<>());
            result.put("bearishRisks", new ArrayList<>());
            result.put("analysis", "");
        }
        return result;
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
    private Map<String, Object> callGroqApi(String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            
            // Create the request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "llama-3.3-70b-versatile");
            requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            requestBody.put("temperature", 0.3); // Lower temperature for more consistent responses
            requestBody.put("max_tokens", 1000);
            requestBody.put("top_p", 0.9);
            requestBody.put("stream", false);
            
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
            } catch (Exception e) {
                log.warn("Could not log response body: {}", e.getMessage());
            }
            
            // Extract the content from the response
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> messageResponse = (Map<String, Object>) choice.get("message");
                
                if (messageResponse != null && messageResponse.containsKey("content")) {
                    String content = (String) messageResponse.get("content");
                    return Map.of("content", content);
                }
            }
            
            log.error("Could not extract content from Groq API response");
            return null;
            
        } catch (Exception e) {
            log.error("Error calling Groq API: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Creates a prompt for Groq requesting a full structured JSON analysis.
     */
    private String createGroqPrompt(Stock stock, StockDetails details, List<NewsArticle> newsArticles) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a senior financial analyst. Analyze this stock and respond ONLY with valid JSON.\n\n");
        prompt.append("Required JSON schema (respond with ONLY this JSON object, no markdown, no code blocks):\n");
        prompt.append("{\n");
        prompt.append("  \"sentiment\": \"POSITIVE\" or \"NEGATIVE\" or \"NEUTRAL\",\n");
        prompt.append("  \"confidence\": <number 0.0-1.0>,\n");
        prompt.append("  \"summary\": \"<2-3 paragraphs of specific analysis that references the actual numbers provided>\",\n");
        prompt.append("  \"bullishPoints\": [\"<specific bullish signal 1>\", \"<specific bullish signal 2>\", \"<specific bullish signal 3>\"],\n");
        prompt.append("  \"bearishRisks\": [\"<specific risk 1>\", \"<specific risk 2>\"],\n");
        prompt.append("  \"outlook\": \"<one sentence short-term outlook>\"\n");
        prompt.append("}\n\n");

        prompt.append("==== STOCK DATA ====\n");
        prompt.append("Symbol: ").append(stock.getSymbol()).append("\n");

        if (details != null && details.getName() != null) {
            prompt.append("Company: ").append(details.getName()).append("\n");
        }
        if (details != null && details.getIndustry() != null) {
            prompt.append("Industry: ").append(details.getIndustry()).append("\n");
        }

        prompt.append("Current Price: $").append(String.format("%.2f", stock.getPrice())).append("\n");
        prompt.append("Change Today: ").append(String.format("%+.2f%%", stock.getChangePercent())).append("\n");
        prompt.append("Volume: ").append(formatNumber(stock.getVolume())).append("\n");

        if (details != null && details.getMarketCap() != null && details.getMarketCap() > 0) {
            // marketCap from StockDetails is in millions
            double capInB = details.getMarketCap() / 1000.0;
            if (capInB >= 1000) {
                prompt.append("Market Cap: $").append(String.format("%.2f", capInB / 1000.0)).append(" T\n");
            } else {
                prompt.append("Market Cap: $").append(String.format("%.2f", capInB)).append(" B\n");
            }
        }
        if (details != null && details.getPeRatio() != null && details.getPeRatio() > 0) {
            prompt.append("P/E Ratio: ").append(String.format("%.2f", details.getPeRatio())).append("\n");
        }
        if (details != null && details.getBeta() != null && details.getBeta() != 0) {
            prompt.append("Beta: ").append(String.format("%.2f", details.getBeta())).append("\n");
        }

        if (stock.getRsi() > 0) {
            prompt.append("RSI: ").append(String.format("%.1f", stock.getRsi()))
                  .append(" (>70 overbought, <30 oversold)\n");
        }
        if (stock.getMacd() != 0) {
            prompt.append("MACD: ").append(String.format("%.4f", stock.getMacd()))
                  .append(stock.getMacd() > 0 ? " (bullish)" : " (bearish)").append("\n");
        }
        if (stock.getSma20() > 0) {
            double pct = ((stock.getPrice() / stock.getSma20()) - 1) * 100;
            prompt.append("SMA20: $").append(String.format("%.2f", stock.getSma20()))
                  .append(" (price ").append(pct > 0 ? "above" : "below")
                  .append(" by ").append(String.format("%.1f%%", Math.abs(pct))).append(")\n");
        }
        if (stock.getSma50() > 0) {
            double pct = ((stock.getPrice() / stock.getSma50()) - 1) * 100;
            prompt.append("SMA50: $").append(String.format("%.2f", stock.getSma50()))
                  .append(" (price ").append(pct > 0 ? "above" : "below")
                  .append(" by ").append(String.format("%.1f%%", Math.abs(pct))).append(")\n");
        }

        if (newsArticles != null && !newsArticles.isEmpty()) {
            prompt.append("\n==== RECENT NEWS ====\n");
            int count = 0;
            for (NewsArticle article : newsArticles) {
                if (count >= 5) break;
                prompt.append(count + 1).append(". ").append(article.getTitle());
                if (article.getPublishedDate() != null) {
                    prompt.append(" [").append(article.getPublishedDate()).append("]");
                }
                prompt.append("\n");
                count++;
            }
        }

        prompt.append("\nRespond with ONLY the JSON object. No explanation, no markdown, no code blocks.");
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
        
        try {
            // Convert text to uppercase for case-insensitive matching
            String upperText = text.toUpperCase();
            
            // Look for sentiment pattern
            if (upperText.contains("SENTIMENT:") && upperText.contains("CONFIDENCE:")) {
                // Extract sentiment
                int sentimentStart = upperText.indexOf("SENTIMENT:") + 10;
                int sentimentEnd = upperText.indexOf(" ", sentimentStart);
                if (sentimentEnd == -1) {
                    // Try looking for newline if space not found
                    sentimentEnd = upperText.indexOf("\n", sentimentStart);
                }
                
                if (sentimentEnd != -1) {
                    String extractedSentiment = upperText.substring(sentimentStart, sentimentEnd).trim();
                    // Validate the sentiment is one of our expected values
                    if (extractedSentiment.equals("POSITIVE") || 
                        extractedSentiment.equals("NEGATIVE") || 
                        extractedSentiment.equals("NEUTRAL")) {
                        sentiment = extractedSentiment;
                    } else {
                        log.warn("Invalid sentiment value extracted: {}", extractedSentiment);
                    }
                }
                
                // Extract confidence
                int confidenceStart = upperText.indexOf("CONFIDENCE:") + 11;
                int confidenceEnd = upperText.indexOf("\n", confidenceStart);
                if (confidenceEnd == -1) confidenceEnd = upperText.indexOf(" ", confidenceStart);
                if (confidenceEnd == -1) confidenceEnd = upperText.length(); // value at end of string

                if (confidenceEnd > confidenceStart) {
                    try {
                        String confidenceStr = upperText.substring(confidenceStart, confidenceEnd).trim();
                        confidence = Double.parseDouble(confidenceStr);
                        // Ensure confidence is between 0 and 1
                        confidence = Math.max(0.0, Math.min(1.0, confidence));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid confidence value extracted: {}", e.getMessage());
                    }
                }
            } else {
                // Enhanced sentiment extraction if format doesn't match
                // Look for sentiment indicators in the text
                int positiveIndicators = 0;
                int negativeIndicators = 0;
                
                // Positive indicators for financial news
                if (upperText.contains("POSITIVE") || upperText.contains("BULLISH") || 
                    upperText.contains("GAIN") || upperText.contains("RISE") || 
                    upperText.contains("UP") || upperText.contains("STRONG") || 
                    upperText.contains("BEAT") || upperText.contains("EXCEED") ||
                    upperText.contains("PROFIT") || upperText.contains("GROWTH") ||
                    upperText.contains("SURGE") || upperText.contains("SOAR") ||
                    upperText.contains("INCREASE") || upperText.contains("IMPROVE") ||
                    upperText.contains("SUCCESS") || upperText.contains("LEAD") ||
                    upperText.contains("EXPAND") || upperText.contains("PARTNERSHIP")) {
                    positiveIndicators++;
                }
                
                // Negative indicators for financial news
                if (upperText.contains("NEGATIVE") || upperText.contains("BEARISH") || 
                    upperText.contains("DROP") || upperText.contains("FALL") || 
                    upperText.contains("DOWN") || upperText.contains("WEAK") || 
                    upperText.contains("MISS") || upperText.contains("BELOW") ||
                    upperText.contains("LOSS") || upperText.contains("DECLINE") ||
                    upperText.contains("PLUNGE") || upperText.contains("SLUMP") ||
                    upperText.contains("DECREASE") || upperText.contains("DETERIORATE") ||
                    upperText.contains("FAILURE") || upperText.contains("LAG") ||
                    upperText.contains("CONTRACT") || upperText.contains("LAWSUIT") ||
                    upperText.contains("INVESTIGATION") || upperText.contains("REGULATORY")) {
                    negativeIndicators++;
                }
                
                // Determine sentiment based on indicators
                if (positiveIndicators > negativeIndicators) {
                    sentiment = "POSITIVE";
                    confidence = 0.7 + (positiveIndicators - negativeIndicators) * 0.05;
                } else if (negativeIndicators > positiveIndicators) {
                    sentiment = "NEGATIVE";
                    confidence = 0.7 + (negativeIndicators - positiveIndicators) * 0.05;
                } else if (positiveIndicators == 0 && negativeIndicators == 0) {
                    // Only set to neutral if no indicators found
                    sentiment = "NEUTRAL";
                    confidence = 0.5;
                } else {
                    // Mixed signals, use the stronger signal
                    sentiment = positiveIndicators > negativeIndicators ? "POSITIVE" : "NEGATIVE";
                    confidence = 0.6;
                }
            }
            
            // Always set both sentiment and confidence in the result
            result.put("sentiment", sentiment);
            result.put("confidence", confidence);
            
            log.debug("Extracted sentiment: {} with confidence: {}", sentiment, confidence);
        } catch (Exception e) {
            log.error("Error extracting sentiment from text: {}", e.getMessage());
            // Set default values in case of error
            result.put("sentiment", "NEUTRAL");
            result.put("confidence", 0.5);
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
    
    
    /**
     * Analyzes sentiment of news articles using LLM
     * @param symbol The stock symbol
     * @param newsArticles List of news articles to analyze
     * @return A map containing sentiment analysis results
     */
    public Map<String, Object> analyzeNewsSentiment(String symbol, List<com.borsvy.model.NewsArticle> newsArticles) {
        log.info("Starting news sentiment analysis for symbol: {}", symbol);
        
        if (newsArticles == null || newsArticles.isEmpty()) {
            log.warn("No news articles provided for sentiment analysis");
            return Map.of(
                "sentiment", "NEUTRAL",
                "confidence", 0.5,
                "summary", "No news articles available for analysis"
            );
        }

        try {
            // Create a prompt for the LLM
            String prompt = createNewsSentimentPrompt(symbol, newsArticles);
            
            // Call Groq API
            Map<String, Object> response = callGroqApi(prompt);
            
            if (response == null || response.isEmpty()) {
                log.error("Failed to get response from Groq API");
                throw new RuntimeException("Failed to get response from Groq API");
            }

            // Extract sentiment and confidence from the response
            Map<String, Object> sentimentData = extractSentimentFromText((String) response.get("content"));
            String sentiment = (String) sentimentData.get("sentiment");
            double confidence = (double) sentimentData.get("confidence");
            
            // Generate a summary of the analysis
            String summary = generateNewsSentimentSummary(symbol, newsArticles, sentiment, confidence);
            
            log.info("News sentiment analysis completed - Sentiment: {}, Confidence: {}", sentiment, confidence);
            
            return Map.of(
                "sentiment", sentiment,
                "confidence", confidence,
                "summary", summary
            );
            
        } catch (Exception e) {
            log.error("Error in news sentiment analysis: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to analyze news sentiment: " + e.getMessage());
        }
    }
    
    /**
     * Creates a prompt for the LLM to analyze news article sentiments
     */
    private String createNewsSentimentPrompt(String symbol, List<com.borsvy.model.NewsArticle> newsArticles) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are a financial news analyst specialized in sentiment analysis. ");
        prompt.append("Analyze the sentiment in these news headlines about ").append(symbol).append(".\n\n");
        
        // Clear instructions about sentiment classification
        prompt.append("SENTIMENT GUIDELINES:\n");
        prompt.append("POSITIVE: Headlines indicating growth, gains, profits, success, market leadership, positive earnings, new products/services, expansion, partnerships, or positive regulatory developments.\n");
        prompt.append("NEGATIVE: Headlines indicating losses, declines, layoffs, regulatory issues, lawsuits, investigations, market share loss, product failures, or negative earnings.\n");
        prompt.append("NEUTRAL: Headlines that are purely informational, announcements without clear positive/negative implications, or balanced news with both positive and negative aspects.\n\n");
        
        // The specific instruction with format requirements
        prompt.append("IMPORTANT: Your response must follow this exact format:\n");
        prompt.append("SENTIMENT:[SENTIMENT] CONFIDENCE:[CONFIDENCE]\n");
        prompt.append("where [SENTIMENT] is exactly one of: POSITIVE, NEGATIVE, or NEUTRAL\n");
        prompt.append("and [CONFIDENCE] is a number between 0 and 1 representing your confidence level.\n\n");
        
        prompt.append("Example responses:\n");
        prompt.append("For positive news: SENTIMENT:POSITIVE CONFIDENCE:0.85\n");
        prompt.append("For negative news: SENTIMENT:NEGATIVE CONFIDENCE:0.75\n");
        prompt.append("For neutral news: SENTIMENT:NEUTRAL CONFIDENCE:0.60\n\n");
        
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
        prompt.append("Consider the following when analyzing:\n");
        prompt.append("1. Financial impact: How would this news likely affect the stock price?\n");
        prompt.append("2. Market reaction: Would investors view this as positive or negative?\n");
        prompt.append("3. Business implications: Does this indicate growth or decline in the business?\n");
        prompt.append("4. Competitive position: Does this strengthen or weaken the company's market position?\n\n");
        prompt.append("IMPORTANT: Avoid defaulting to NEUTRAL unless there is a clear balance of positive and negative news or truly neutral content.\n");
        prompt.append("Be decisive in your sentiment classification - if there is any clear positive or negative trend, classify accordingly.\n");
        prompt.append("First provide the SENTIMENT and CONFIDENCE, then the article breakdown.");
        
        return prompt.toString();
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