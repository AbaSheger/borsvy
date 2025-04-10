package com.borsvy.client;

import com.borsvy.model.NewsArticle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.asynchttpclient.Dsl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.Arrays;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.MalformedURLException;

/**
 * Client for making API calls to Yahoo Finance via RapidAPI
 */
@Component
public class RapidApiClient {
    private static final Logger log = LoggerFactory.getLogger(RapidApiClient.class);
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_DATE_TIME;
    private static final int DEFAULT_LIMIT = 10;
    
    @Value("${rapidapi.api.key}")
    private String apiKey;

    @Value("${rapidapi.api.host}")
    private String apiHost;
    
    private final ObjectMapper objectMapper;

    public RapidApiClient() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Gets financial news articles for a stock
     */
    public List<NewsArticle> getStockNews(String symbol) {
        return getStockNews(symbol, 10);
    }
    
    public List<NewsArticle> getStockNews(String symbol, int limit) {
        // Use the updated news API method
        return getNewsViaNewsAPI(symbol, limit > 0 ? limit : DEFAULT_LIMIT);
    }
    
    /**
     * Gets news sentiment analysis for a stock
     */
    public Map<String, Object> getNewsSentiment(String symbol) {
        try (AsyncHttpClient client = new DefaultAsyncHttpClient()) {
            String url = "https://yahoo-finance166.p.rapidapi.com/api/news/list-by-symbol";
            log.info("Making sentiment request to URL: {} with symbol: {}", url, symbol);
            log.info("Using API Key: {} and Host: {}", apiKey.substring(0, 5) + "...", apiHost);
            
            return client.prepare("GET", url)
                .addQueryParam("s", symbol)
                .addQueryParam("region", "US")
                .setHeader("x-rapidapi-key", apiKey)
                .setHeader("x-rapidapi-host", "yahoo-finance166.p.rapidapi.com")
                .execute()
                .toCompletableFuture()
                .thenApply(response -> {
                    try {
                        log.info("Received response with status code: {}", response.getStatusCode());
                        if (response.getStatusCode() != 200) {
                            log.error("API returned non-200 status code: {}", response.getStatusCode());
                            log.error("Response body: {}", response.getResponseBody());
                            return createErrorResponse("API returned status code: " + response.getStatusCode());
                        }
                        
                        // Log response for debugging
                        String responseBody = response.getResponseBody();
                        log.debug("Raw sentiment API response: {}", responseBody);
                        
                        JsonNode root = objectMapper.readTree(responseBody);
                        log.info("Parsed JSON response. Root keys: {}", 
                            StreamSupport.stream(Spliterators.spliteratorUnknownSize(root.fieldNames(), Spliterator.ORDERED), false)
                                .collect(Collectors.joining(", ")));
                        
                        // Determine where the news items are located in the response
                        JsonNode newsArray = null;
                        
                        if (root.has("body") && root.get("body").isArray()) {
                            newsArray = root.get("body");
                            log.info("Found sentiment items in 'body' array");
                        } else if (root.isArray()) {
                            newsArray = root;
                            log.info("Found sentiment items in root array");
                        } else if (root.has("data") && root.get("data").isObject()) {
                            if (root.get("data").has("main") && root.get("data").get("main").has("stream")) {
                                newsArray = root.get("data").get("main").get("stream");
                                log.info("Found sentiment items in 'data.main.stream' array");
                            } else if (root.get("data").has("stream")) {
                                newsArray = root.get("data").get("stream");
                                log.info("Found sentiment items in 'data.stream' array");
                            } else if (root.get("data").has("news") && root.get("data").get("news").isArray()) {
                                newsArray = root.get("data").get("news");
                                log.info("Found sentiment items in 'data.news' array");
                            } 
                        }
                        
                        if (newsArray == null) {
                            log.warn("No parsable news array found for sentiment analysis. Root keys: {}", 
                                StreamSupport.stream(Spliterators.spliteratorUnknownSize(
                                    root.fieldNames(), Spliterator.ORDERED), false)
                                .collect(Collectors.joining(", ")));
                            return createErrorResponse("No news data available for sentiment analysis");
                        }
                        
                        int positiveCount = 0;
                        int negativeCount = 0;
                        int neutralCount = 0;
                        List<Map<String, Object>> analyzedArticles = new ArrayList<>();
                        
                        for (JsonNode article : newsArray) {
                            String title = article.has("title") ? article.get("title").asText("") : "";
                            String summary = article.has("text") ? article.get("text").asText("") : "";
                            
                            // Log the article for debugging
                            log.info("Analyzing article: Title='{}', Summary='{}'", 
                                title.length() > 50 ? title.substring(0, 50) + "..." : title,
                                summary.length() > 50 ? summary.substring(0, 50) + "..." : summary);
                            
                            // Analyze both title and summary
                            boolean isPositive = containsPositiveKeywords(title + " " + summary);
                            boolean isNegative = containsNegativeKeywords(title + " " + summary);
                            
                            Map<String, Object> analyzedArticle = new HashMap<>();
                            analyzedArticle.put("title", title);
                            analyzedArticle.put("sentiment", isPositive ? "positive" : isNegative ? "negative" : "neutral");
                            
                            // Log the sentiment finding
                            log.info("Article sentiment: {} (positive={}, negative={})", 
                                isPositive ? "positive" : isNegative ? "negative" : "neutral",
                                isPositive, isNegative);
                            
                            if (isPositive) {
                                positiveCount++;
                            } else if (isNegative) {
                                negativeCount++;
                            } else {
                                neutralCount++;
                            }
                            
                            analyzedArticles.add(analyzedArticle);
                        }
                        
                        int total = positiveCount + negativeCount + neutralCount;
                        if (total == 0) return createErrorResponse("No sentiment data available");
                        
                        // Calculate sentiment score with adjusted thresholds
                        double score = (double)(positiveCount - negativeCount) / total;
                        String overallSentiment;
                        if (score > 0.2) overallSentiment = "bullish";
                        else if (score > 0.05) overallSentiment = "slightly bullish";
                        else if (score < -0.2) overallSentiment = "bearish";
                        else if (score < -0.05) overallSentiment = "slightly bearish";
                        else overallSentiment = "neutral";
                        
                        // Create detailed response
                        Map<String, Object> result = new HashMap<>();
                        result.put("sentiment", overallSentiment);
                        result.put("positiveCount", positiveCount);
                        result.put("negativeCount", negativeCount);
                        result.put("neutralCount", neutralCount);
                        result.put("totalArticles", total);
                        result.put("score", score);
                        result.put("analyzedArticles", analyzedArticles);
                        
                        log.info("Sentiment analysis results - Positive: {}, Negative: {}, Neutral: {}, Overall: {}", 
                            positiveCount, negativeCount, neutralCount, overallSentiment);
                        
                        return result;
                    } catch (Exception e) {
                        log.error("Error analyzing sentiment: {}", e.getMessage());
                        return createErrorResponse("Error analyzing sentiment");
                    }
                })
                .exceptionally(t -> {
                    log.error("Exception in sentiment analysis: {}", t.getMessage());
                    return createErrorResponse("Error fetching sentiment");
                })
                .join();
        } catch (Exception e) {
            log.error("Error in sentiment analysis: {}", e.getMessage());
            return createErrorResponse("Error analyzing sentiment");
        }
    }
    
    /**
     * Gets news via the specific symbol news API
     */
    private List<NewsArticle> getNewsViaNewsAPI(String symbol, int limit) {
        log.info("Getting stock news via News API for symbol: {} with limit: {}", symbol, limit);
        List<NewsArticle> articles = new ArrayList<>();
        
        try (AsyncHttpClient client = new DefaultAsyncHttpClient()) {
            // Get the company name for better filtering
            String companyName = getCompanyNameForSymbol(symbol);
            
            // Create URL for Yahoo Finance API
            String url = "https://yahoo-finance166.p.rapidapi.com/api/news/list-by-symbol";
            log.info("Making news API request to URL: {}", url);
            
            return client.prepare("GET", url)
                .addQueryParam("s", symbol)
                .addQueryParam("region", "US")
                .setHeader("x-rapidapi-key", apiKey)
                .setHeader("x-rapidapi-host", "yahoo-finance166.p.rapidapi.com")
                .execute()
                .toCompletableFuture()
                .thenApply(response -> {
                    try {
                        log.info("Received News API response with status code: {}", response.getStatusCode());
                        
                        if (response.getStatusCode() != 200) {
                            log.error("News API returned non-200 status code: {}", response.getStatusCode());
                            return articles;
                        }
                        
                        // Parse the response
                        String responseBody = response.getResponseBody();
                        log.debug("Raw news API response: {}", responseBody);
                        
                        JsonNode root = objectMapper.readTree(responseBody);
                        
                        // Determine which endpoint response format this is
                        List<NewsArticle> processedArticles = new ArrayList<>();
                        
                        // Find the data structure
                        if (root.has("body") && root.get("body").isArray()) {
                            log.info("Using 'body' array for news items");
                            processNewsArray(root.get("body"), processedArticles, symbol, companyName, limit);
                        } else if (root.isArray()) {
                            log.info("Using root array for news items");
                            processNewsArray(root, processedArticles, symbol, companyName, limit);
                        } else if (root.has("data") && root.get("data").isObject()) {
                            if (root.get("data").has("main") && root.get("data").get("main").has("stream")) {
                                log.info("Using 'data.main.stream' array for news items");
                                processNewsArray(root.get("data").get("main").get("stream"), processedArticles, symbol, companyName, limit);
                            } else if (root.get("data").has("stream")) {
                                log.info("Using 'data.stream' array for news items");
                                processNewsArray(root.get("data").get("stream"), processedArticles, symbol, companyName, limit);
                            } else if (root.get("data").has("news") && root.get("data").get("news").isArray()) {
                                log.info("Using 'data.news' array for news items");
                                processNewsArray(root.get("data").get("news"), processedArticles, symbol, companyName, limit);
                            } else {
                                log.info("No recognizable news array found in data structure");
                            }
                        } else {
                            log.warn("Unrecognized news response structure. Available fields: {}", 
                                StreamSupport.stream(Spliterators.spliteratorUnknownSize(
                                    root.fieldNames(), Spliterator.ORDERED), false)
                                .collect(Collectors.joining(", ")));
                        }
                        
                        log.info("Processed {} news articles for {}", processedArticles.size(), symbol);
                        return processedArticles;
                    } catch (Exception e) {
                        log.error("Error processing news API response: {}", e.getMessage(), e);
                        return articles;
                    }
                })
                .exceptionally(t -> {
                    log.error("Exception fetching news from yahoo-finance166: {}", t.getMessage(), t);
                    return new ArrayList<>();
                })
                .join();
        } catch (Exception e) {
            log.error("Error setting up news API call to yahoo-finance166: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    private void processNewsArray(JsonNode newsArray, List<NewsArticle> articles, String symbol, String companyName, int limit) {
        int count = 0;
        
        for (JsonNode item : newsArray) {
            if (count >= limit) break;
            
            try {
                NewsArticle article = extractArticleV2(item);
                
                if (article != null && article.getTitle() != null && !article.getTitle().isEmpty()) {
                    // Get the title and summary for sentiment analysis
                    String title = article.getTitle();
                    String summary = article.getSummary();
                    String fullText = (title + " " + summary).toLowerCase();
                    
                    // Count positive and negative keywords
                    int positiveCount = countKeywords(fullText, true);
                    int negativeCount = countKeywords(fullText, false);
                    
                    // Log the counts for debugging
                    log.info("Article '{}' keyword counts: positive={}, negative={}", 
                        title.length() > 40 ? title.substring(0, 40) + "..." : title,
                        positiveCount, negativeCount);
                    
                    // Determine sentiment based on keyword counts and specific patterns
                    String sentiment = "neutral";
                    
                    // Check for strong negative indicators first
                    if (containsStrongNegativeIndicators(fullText)) {
                        sentiment = "negative";
                    }
                    // Check for strong positive indicators
                    else if (containsStrongPositiveIndicators(fullText)) {
                        sentiment = "positive";
                    }
                    // Otherwise, base on keyword counts with a threshold
                    else if (positiveCount > negativeCount && positiveCount >= 2) {
                        sentiment = "positive";
                    } 
                    else if (negativeCount > positiveCount && negativeCount >= 2) {
                        sentiment = "negative";
                    }
                    
                    // Set sentiment in the article object
                    article.setSentiment(sentiment);
                    
                    log.info("Final article sentiment for '{}': {}", 
                        title.length() > 40 ? title.substring(0, 40) + "..." : title,
                        sentiment);
                    
                    if (!isDuplicate(articles, article)) {
                        articles.add(article);
                        count++;
                    }
                }
            } catch (Exception e) {
                log.warn("Error extracting article from item: {}", e.getMessage());
            }
        }
    }
    
    // Helper method to count keywords
    private int countKeywords(String text, boolean positiveKeywords) {
        String[] keywords = positiveKeywords ? getPositiveKeywords() : getNegativeKeywords();
        int count = 0;
        
        for (String keyword : keywords) {
            // Count how many times the keyword appears
            String lowerKeyword = keyword.toLowerCase();
            
            // For multi-word phrases, do exact matching
            if (lowerKeyword.contains(" ")) {
                if (text.contains(lowerKeyword)) {
                    count++;
                    log.debug("Found {} keyword phrase: '{}'", 
                        positiveKeywords ? "positive" : "negative", keyword);
                }
            } 
            // For single words, check for word boundaries to avoid partial matches
            else {
                String wordPattern = "\\b" + Pattern.quote(lowerKeyword) + "\\b";
                Pattern pattern = Pattern.compile(wordPattern);
                Matcher matcher = pattern.matcher(text);
                
                while (matcher.find()) {
                    count++;
                    log.debug("Found {} keyword: '{}'", 
                        positiveKeywords ? "positive" : "negative", keyword);
                }
            }
        }
        
        return count;
    }
    
    // Strong negative indicators that should override other analysis
    private boolean containsStrongNegativeIndicators(String text) {
        String[] strongNegatives = {
            "stock down", "stocks down", "shares down", "shares fall", "stock falls", "stocks fall",
            "market crash", "stock crash", "shares crash", "sell-off", "selling off",
            "worst day", "worst week", "worst month", "big drop", "sharp decline",
            "heavy losses", "major losses", "tumbles", "plunges", "disaster",
            "disappointing earnings", "missed expectations", "below forecast",
            "layoffs", "job cuts", "bankruptcy", "class action", "fraud", "investigation"
        };
        
        for (String phrase : strongNegatives) {
            if (text.contains(phrase)) {
                log.debug("Found strong negative indicator: '{}'", phrase);
                return true;
            }
        }
        
        return false;
    }
    
    // Strong positive indicators that should override other analysis
    private boolean containsStrongPositiveIndicators(String text) {
        String[] strongPositives = {
            "stock up", "stocks up", "shares up", "shares rise", "stock rises", "stocks rise",
            "breakout", "record high", "all-time high", "new high", "multi-year high",
            "beats expectations", "exceeds forecast", "strong earnings", "strong quarter",
            "dividend increase", "raised guidance", "buy rating", "strong buy",
            "best day", "best week", "best month", "big gain", "sharp increase",
            "major gains", "soars", "surges", "rallies", "jumps"
        };
        
        for (String phrase : strongPositives) {
            if (text.contains(phrase)) {
                log.debug("Found strong positive indicator: '{}'", phrase);
                return true;
            }
        }
        
        return false;
    }
    
    // Get a refined list of positive keywords
    private String[] getPositiveKeywords() {
        return new String[] {
            // Strong positive financial terms
            "rally", "surge", "soar", "jump", "beat expectations", "exceed estimates",
            "upgrade", "bullish", "buy rating", "outperform", "strong growth",
            "record revenue", "record profit", "market leader", "increased dividend",
            "profit", "profitable", "promising", "momentum", "recovery", "breakthrough"
        };
    }
    
    // Get a refined list of negative keywords
    private String[] getNegativeKeywords() {
        return new String[] {
            // Strong negative financial terms
            "decline", "drop", "fall", "slip", "slump", "tumble", "plunge", "crash",
            "bearish", "downgrade", "sell rating", "underperform", "miss expectations",
            "below estimates", "downside", "negative", "weak", "loss", "struggling",
            "concern", "risk", "uncertainty", "volatility", "warning", "crisis"
        };
    }
    
    /**
     * Extract article data from JSON node
     */
    private NewsArticle extractArticle(JsonNode item) {
        try {
            String title = item.get("title").asText();
            String url = item.get("link").asText();
            String source = item.get("source").asText();
            String publishedDate = formatDate(item.get("pubDate").asText());
            String summary = item.has("description") ? item.get("description").asText() : "";
            
            // Use a default thumbnail if none is available
            String thumbnail = getDefaultThumbnailForSource(source);
            
            return new NewsArticle(title, summary, url, thumbnail);
        } catch (Exception e) {
            log.error("Error extracting article: {}", e.getMessage());
            return null;
        }
    }
    
    private String extractImageFromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        // Look for img tags
        Pattern imgPattern = Pattern.compile("<img[^>]+src=\"([^\">]+)\"");
        Matcher imgMatcher = imgPattern.matcher(text);
        if (imgMatcher.find()) {
            String imgUrl = imgMatcher.group(1);
            if (isValidImageUrl(imgUrl)) {
                return imgUrl;
            }
        }
        
        // Look for image URLs in the text
        Pattern urlPattern = Pattern.compile("https?://[^\\s<>]+?\\.(jpg|jpeg|png|gif)");
        Matcher urlMatcher = urlPattern.matcher(text);
        if (urlMatcher.find()) {
            String imgUrl = urlMatcher.group();
            if (isValidImageUrl(imgUrl)) {
                return imgUrl;
            }
        }
        
        return null;
    }

    private boolean isValidImageUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        // Check if URL is valid
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            return false;
        }
        
        // Check if URL points to an image
        return url.matches(".*\\.(jpg|jpeg|png|gif)(\\?.*)?$");
    }
    
    private String extractThumbnailFromUrl(String url) {
        try {
            log.info("Attempting to extract thumbnail from URL: {}", url);
            
            // Try to get the article's main image using a simple HTML parser
            Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .timeout(10000)
                .get();
            
            // Try different meta tags for images
            String thumbnail = null;
            
            // 1. Try Open Graph image
            Element metaImage = doc.select("meta[property=og:image]").first();
            if (metaImage != null) {
                thumbnail = metaImage.attr("content");
                log.info("Found Open Graph image: {}", thumbnail);
            }
            
            // 2. Try Twitter card image
            if (thumbnail == null) {
                metaImage = doc.select("meta[name=twitter:image]").first();
                if (metaImage != null) {
                    thumbnail = metaImage.attr("content");
                    log.info("Found Twitter card image: {}", thumbnail);
                }
            }
            
            // 3. Try article:image meta tag
            if (thumbnail == null) {
                metaImage = doc.select("meta[property=article:image]").first();
                if (metaImage != null) {
                    thumbnail = metaImage.attr("content");
                    log.info("Found article:image: {}", thumbnail);
                }
            }
            
            // 4. Try to find the first large image in the article
            if (thumbnail == null) {
                // Look for images with specific classes or IDs that typically indicate main content
                Element mainImage = doc.select("img.article-image, img.article-img, img.main-image, img.featured-image, img.hero-image").first();
                if (mainImage == null) {
                    // If no specific class found, look for the first large image
                    mainImage = doc.select("img[src~=(?i)\\.(png|jpe?g|gif)]").stream()
                        .filter(img -> {
                            String width = img.attr("width");
                            String height = img.attr("height");
                            return (width != null && Integer.parseInt(width) >= 300) || 
                                   (height != null && Integer.parseInt(height) >= 200);
                        })
                        .findFirst()
                        .orElse(null);
                }
                
                if (mainImage != null) {
                    thumbnail = mainImage.attr("src");
                    // Handle relative URLs
                    if (thumbnail.startsWith("/")) {
                        thumbnail = new URL(new URL(url), thumbnail).toString();
                    }
                    log.info("Found main content image: {}", thumbnail);
                }
            }
            
            // 5. Try to find any image in the article content
            if (thumbnail == null) {
                Element articleImage = doc.select("article img, .article-content img, .post-content img").first();
                if (articleImage != null) {
                    thumbnail = articleImage.attr("src");
                    // Handle relative URLs
                    if (thumbnail.startsWith("/")) {
                        thumbnail = new URL(new URL(url), thumbnail).toString();
                    }
                    log.info("Found article content image: {}", thumbnail);
                }
            }
            
            // Validate the thumbnail URL
            if (thumbnail != null) {
                // Check if the URL is absolute
                if (!thumbnail.startsWith("http")) {
                    thumbnail = new URL(new URL(url), thumbnail).toString();
                }
                
                // Check if the image URL is accessible
                try {
                    HttpURLConnection connection = (HttpURLConnection) new URL(thumbnail).openConnection();
                    connection.setRequestMethod("HEAD");
                    int responseCode = connection.getResponseCode();
                    if (responseCode != 200) {
                        log.warn("Thumbnail URL not accessible (HTTP {}): {}", responseCode, thumbnail);
                        thumbnail = null;
                    } else {
                        log.info("Successfully validated thumbnail URL: {}", thumbnail);
                    }
                } catch (Exception e) {
                    log.warn("Error checking thumbnail URL: {} - {}", thumbnail, e.getMessage());
                    thumbnail = null;
                }
            } else {
                log.warn("No thumbnail found for URL: {}", url);
            }
            
            return thumbnail;
        } catch (Exception e) {
            log.error("Failed to extract thumbnail from URL {}: {}", url, e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract article data from v2 API response
     */
    private NewsArticle extractArticleV2(JsonNode item) {
        try {
            log.debug("Processing news item: {}", item.toString());
            
            // Check if the item has a nested content object (new structure)
            JsonNode contentNode = item;
            if (item.has("content") && item.get("content").isObject()) {
                contentNode = item.get("content");
                log.debug("Found nested content structure in news item");
            }
            
            // Extract required fields
            String title = contentNode.has("title") ? contentNode.get("title").asText("") : "";
            if (title.isEmpty()) return null;
            
            // Handle new nested URL structure
            String link = "";
            if (contentNode.has("link")) {
                link = contentNode.get("link").asText("");
            } else if (contentNode.has("url")) {
                link = contentNode.get("url").asText("");
            } else if (contentNode.has("clickThroughUrl") && contentNode.get("clickThroughUrl").has("url")) {
                link = contentNode.get("clickThroughUrl").get("url").asText("");
            } else if (contentNode.has("canonicalUrl") && contentNode.get("canonicalUrl").has("url")) {
                link = contentNode.get("canonicalUrl").get("url").asText("");
            }
            
            if (link.isEmpty()) return null;
            
            // Create article
            NewsArticle article = new NewsArticle();
            article.setTitle(title);
            article.setUrl(link);
            
            // Publisher/source
            String source = "Yahoo Finance";
            if (contentNode.has("source") && !contentNode.get("source").isNull()) {
                source = contentNode.get("source").asText("Yahoo Finance");
            } else if (contentNode.has("publisher") && !contentNode.get("publisher").isNull()) {
                source = contentNode.get("publisher").asText("Yahoo Finance");
            } else if (contentNode.has("provider") && contentNode.get("provider").has("displayName")) {
                source = contentNode.get("provider").get("displayName").asText("Yahoo Finance");
            }
            article.setSource(source);
            
            // Date
            String pubDate = "";
            if (contentNode.has("pubDate")) {
                pubDate = contentNode.get("pubDate").asText("");
            } else if (contentNode.has("published")) {
                pubDate = contentNode.get("published").asText("");
            } else if (contentNode.has("date")) {
                pubDate = contentNode.get("date").asText("");
            }
            article.setPublishedDate(formatDate(pubDate));
            
            // Summary - For new structure, there may not be a dedicated summary field
            String summary = "No summary available";
            if (contentNode.has("description")) {
                summary = contentNode.get("description").asText("No summary available");
            } else if (contentNode.has("summary")) {
                summary = contentNode.get("summary").asText("No summary available");
            } else if (item.has("summary")) {
                // Some APIs put summary at the top level
                summary = item.get("summary").asText("No summary available");
            }
            article.setSummary(summary);
            
            // Enhanced thumbnail handling for v2
            String thumbnail = null;
            
            // Log all available fields to help debug thumbnail issues
            StringBuilder fields = new StringBuilder();
            Iterator<String> fieldNames = contentNode.fieldNames();
            while (fieldNames.hasNext()) {
                fields.append(fieldNames.next()).append(", ");
            }
            log.debug("Available fields in news content: {}", fields.toString());
            
            // Try different possible thumbnail fields in v2 response
            // New structure has a complex thumbnail object with resolutions
            if (contentNode.has("thumbnail") && contentNode.get("thumbnail").isObject() && 
                contentNode.get("thumbnail").has("resolutions") && 
                contentNode.get("thumbnail").get("resolutions").isArray()) {
                
                JsonNode resolutions = contentNode.get("thumbnail").get("resolutions");
                // Try to get the original resolution first
                for (JsonNode resolution : resolutions) {
                    if (resolution.has("tag") && resolution.get("tag").asText("").equals("original") &&
                        resolution.has("url")) {
                        thumbnail = resolution.get("url").asText("");
                        log.debug("Found thumbnail in resolutions with tag 'original': {}", thumbnail);
                        break;
                    }
                }
                
                // If no original found, take the first available one
                if (thumbnail == null && resolutions.size() > 0 && resolutions.get(0).has("url")) {
                    thumbnail = resolutions.get(0).get("url").asText("");
                    log.debug("Found thumbnail in first resolution: {}", thumbnail);
                }
            }
            // Fallback to old structures
            else if (contentNode.has("thumbnail") && !contentNode.get("thumbnail").isNull()) {
                thumbnail = contentNode.get("thumbnail").asText("");
                log.debug("Found thumbnail in simple 'thumbnail' field: {}", thumbnail);
            } else if (item.has("image") && !item.get("image").asText("").isEmpty()) {
                thumbnail = item.get("image").asText("");
                log.debug("Found thumbnail in 'image' field: {}", thumbnail);
            } else if (item.has("imageUrl") && !item.get("imageUrl").asText("").isEmpty()) {
                thumbnail = item.get("imageUrl").asText("");
                log.debug("Found thumbnail in 'imageUrl' field: {}", thumbnail);
            } else if (item.has("main_image") && !item.get("main_image").asText("").isEmpty()) {
                thumbnail = item.get("main_image").asText("");
                log.debug("Found thumbnail in 'main_image' field: {}", thumbnail);
            } else if (item.has("images") && item.get("images").isArray() && item.get("images").size() > 0) {
                thumbnail = item.get("images").get(0).asText("");
                log.debug("Found thumbnail in 'images' array: {}", thumbnail);
            }
            
            if (thumbnail == null || thumbnail.isEmpty()) {
                thumbnail = getDefaultThumbnailForSource(source);
                log.debug("Using default thumbnail for article '{}': {}", title, thumbnail);
            }
            
            article.setThumbnail(thumbnail);
            
            return article;
        } catch (Exception e) {
            log.error("Error extracting article: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Get a default thumbnail based on the news source
     */
    private String getDefaultThumbnailForSource(String source) {
        if (source == null) {
            return getDefaultFinancialThumbnail();
        }
        
        // Map common news sources to their default thumbnails
        Map<String, String> sourceThumbnails = new HashMap<>();
        sourceThumbnails.put("Yahoo Finance", "https://s.yimg.com/ny/api/res/1.2/2Qq8o3Ld_2PqL2K5L1XzGA--/YXBwaWQ9aGlnaGxhbmRlcjt3PTk2MDtoPTU0MDtjZj13ZWJw/https://s.yimg.com/uu/api/res/1.2/QxV2bGVfSVFnb2x3X1F3Z2t3L2h0dHBzOi8vd3d3LnlhaG9vLmNvbS9maW5hbmNlL2ltYWdlcy9kZWZhdWx0L2ZpbmFuY2lhbC1uZXdzLmpwZw--");
        sourceThumbnails.put("Reuters", "https://www.reuters.com/pf/resources/images/reuters/reuters-default.png");
        sourceThumbnails.put("Bloomberg", "https://assets.bwbx.io/s3/javelin/public/javelin/images/social-default-a4f15fa7ee.jpg");
        sourceThumbnails.put("CNBC", "https://www.cnbc.com/pf/resources/images/CNBC_logo_reuters.png");
        sourceThumbnails.put("MarketWatch", "https://s.marketwatch.com/public/resources/images/MW-HP535_market_ZH_20190123153019.jpg");
        sourceThumbnails.put("Business Insider", "https://static.businessinsider.com/image/5d9d8b7c6f24eb1a0a2b3b5a-1200.jpg");
        sourceThumbnails.put("Investor's Business Daily", "https://www.investors.com/wp-content/uploads/2019/01/IBD-logo.png");
        sourceThumbnails.put("The Wall Street Journal", "https://s.wsj.net/img/WSJ_Logo_black_social.png");
        sourceThumbnails.put("Barrons.com", "https://www.barrons.com/assets/img/barrons-logo.png");
        sourceThumbnails.put("Motley Fool", "https://g.foolcdn.com/art/companylogos/square/tmf.png");
        sourceThumbnails.put("Fortune", "https://fortune.com/favicon.ico");
        sourceThumbnails.put("The Real Deal", "https://therealdeal.com/wp-content/uploads/2019/05/trd-logo.png");
        sourceThumbnails.put("Insider Monkey", "https://www.insidermonkey.com/blog/wp-content/uploads/2019/01/insider-monkey-logo.png");
        sourceThumbnails.put("Benzinga", "https://cdn.benzinga.com/files/images/story/2012/benzinga-logo.png");
        sourceThumbnails.put("Investopedia", "https://www.investopedia.com/thmb/0YHt1qQvQw7Ckf6ENJh0QjXF8b4=/1500x0/filters:no_upscale():max_bytes(150000):strip_icc()/InvestopediaLogo-9c5b0a7f0b4b4798b0bfde9d5b0b8b3a.png");
        sourceThumbnails.put("etf.com", "https://www.etf.com/sites/default/files/etf-com-logo.png");
        sourceThumbnails.put("TheStreet", "https://www.thestreet.com/.image/t_share/MTc0NDU4NDg5ODQ5NDQ5NDQ5/thestreet-logo.png");
        sourceThumbnails.put("CIO Dive", "https://www.ciodive.com/img/ciodive-logo.png");
        
        return sourceThumbnails.getOrDefault(source, getDefaultFinancialThumbnail());
    }
    
    /**
     * Format date string to user-friendly format
     */
    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return "Today";
        }
        
        try {
            // Handle ISO format: 2025-04-08T11:06:58Z
            if (dateStr.contains("T") && dateStr.endsWith("Z")) {
                LocalDateTime dateTime = LocalDateTime.parse(dateStr, ISO_DATE_TIME);
                LocalDateTime now = LocalDateTime.now();
                
                if (dateTime.toLocalDate().equals(now.toLocalDate())) {
                    return "Today, " + dateTime.format(DateTimeFormatter.ofPattern("HH:mm"));
                } else if (dateTime.toLocalDate().equals(now.toLocalDate().minusDays(1))) {
                    return "Yesterday";
                } else {
                    return dateTime.format(DateTimeFormatter.ofPattern("MMM d, yyyy"));
                }
            }
            
            return dateStr;
        } catch (Exception e) {
            return dateStr;
        }
    }
    
    /**
     * Check if article is a duplicate
     */
    private boolean isDuplicate(List<NewsArticle> articles, NewsArticle article) {
        return articles.stream()
            .anyMatch(a -> a.getTitle().equalsIgnoreCase(article.getTitle()) ||
                     a.getUrl().equals(article.getUrl()));
    }
    
    /**
     * Check if article is highly relevant to the stock
     */
    private boolean isHighlyRelevant(String title, String symbol, String companyName) {
        String titleLower = title.toLowerCase();
        String symbolLower = symbol.toLowerCase();
        String companyLower = companyName.toLowerCase();

        // Handle special cases for major companies
        if (symbol.equals("GOOGL") || symbol.equals("GOOG")) {
            String[] googleTerms = {"google", "alphabet", "googl", "goog", "tech giant", "search giant", "ai", "artificial intelligence", "cloud", "android"};
            for (String term : googleTerms) {
                if (titleLower.contains(term)) {
                    return true;
                }
            }
        }

        // Check for exact matches first
        if (titleLower.contains(symbolLower) || 
            titleLower.contains(companyLower) || 
            (companyLower.contains("google") && titleLower.contains("alphabet")) ||
            (companyLower.contains("alphabet") && titleLower.contains("google"))) {
            return true;
        }

        // Check for market-related terms with company context
        String[] marketTerms = {
            "stock", "shares", "market", "trading", "price", "investor", 
            "earnings", "revenue", "profit", "growth", "performance",
            "tech", "technology", "digital", "investment", "stake",
            "nasdaq", "wall street", "market cap", "billion", "million"
        };

        // If title contains market terms and mentions major tech companies or "Mag 7"
        if (Arrays.asList("AAPL", "GOOGL", "MSFT", "AMZN", "META", "NVDA", "TSLA").contains(symbol)) {
            if (titleLower.contains("tech") || 
                titleLower.contains("technology") || 
                titleLower.contains("mag 7") || 
                titleLower.contains("magnificent seven")) {
                return true;
            }
        }

        // Check if title contains both market terms and company references
        for (String term : marketTerms) {
            if (titleLower.contains(term) && 
                (titleLower.contains(symbolLower) || titleLower.contains(companyLower))) {
                return true;
            }
        }

        return false;
    }
    
    /**
     * Check if title mentions products related to the stock
     */
    private boolean isProductRelevant(String title, String symbol) {
        switch (symbol) {
            case "AAPL": return title.contains("iphone") || title.contains("ipad") || 
                                title.contains("mac") || title.contains("ios");
            case "MSFT": return title.contains("windows") || title.contains("xbox") || 
                                title.contains("office") || title.contains("azure") || 
                                title.contains("teams");
            case "GOOGL": return title.contains("android") || title.contains("pixel") || 
                                 title.contains("chrome") || title.contains("youtube");
            case "AMZN": return title.contains("aws") || title.contains("prime") || 
                                title.contains("e-commerce");
            case "META": return title.contains("facebook") || title.contains("instagram") || 
                                title.contains("whatsapp") || title.contains("metaverse");
            default: return false;
        }
    }
    
    /**
     * Get default financial thumbnail
     */
    private String getDefaultFinancialThumbnail() {
        return "https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=800&auto=format&fit=crop";
    }
    
    /**
     * Map stock symbol to company name
     */
    private String getCompanyNameForSymbol(String symbol) {
        Map<String, String> companyNames = new HashMap<>();
        // Tech Companies
        companyNames.put("AAPL", "Apple");
        companyNames.put("MSFT", "Microsoft");
        companyNames.put("GOOGL", "Google");
        companyNames.put("GOOG", "Google");
        companyNames.put("AMZN", "Amazon");
        companyNames.put("META", "Meta");
        companyNames.put("NVDA", "Nvidia");
        companyNames.put("TSLA", "Tesla");
        companyNames.put("AMD", "Advanced Micro Devices");
        companyNames.put("INTC", "Intel");
        companyNames.put("CRM", "Salesforce");
        companyNames.put("ADBE", "Adobe");
        companyNames.put("NFLX", "Netflix");
        companyNames.put("CSCO", "Cisco");
        
        // Financial Companies
        companyNames.put("JPM", "JPMorgan Chase");
        companyNames.put("BAC", "Bank of America");
        companyNames.put("WFC", "Wells Fargo");
        companyNames.put("GS", "Goldman Sachs");
        companyNames.put("MS", "Morgan Stanley");
        companyNames.put("V", "Visa");
        companyNames.put("MA", "Mastercard");
        companyNames.put("AXP", "American Express");
        
        // Retail & Consumer
        companyNames.put("WMT", "Walmart");
        companyNames.put("TGT", "Target");
        companyNames.put("COST", "Costco");
        companyNames.put("HD", "Home Depot");
        companyNames.put("LOW", "Lowe's");
        companyNames.put("NKE", "Nike");
        companyNames.put("SBUX", "Starbucks");
        companyNames.put("MCD", "McDonald's");
        
        // Healthcare
        companyNames.put("JNJ", "Johnson & Johnson");
        companyNames.put("PFE", "Pfizer");
        companyNames.put("MRNA", "Moderna");
        companyNames.put("UNH", "UnitedHealth");
        companyNames.put("CVS", "CVS Health");
        
        // Telecom & Media
        companyNames.put("T", "AT&T");
        companyNames.put("VZ", "Verizon");
        companyNames.put("CMCSA", "Comcast");
        companyNames.put("DIS", "Disney");
        
        // Energy & Industrial
        companyNames.put("XOM", "ExxonMobil");
        companyNames.put("CVX", "Chevron");
        companyNames.put("BA", "Boeing");
        companyNames.put("GE", "General Electric");
        companyNames.put("F", "Ford");
        companyNames.put("GM", "General Motors");
        
        // Try to get the exact match first
        String companyName = companyNames.get(symbol);
        if (companyName != null) {
            return companyName;
        }
        
        // If no exact match, try to extract company name from symbol
        // This handles cases where we don't have the symbol in our map
        if (symbol.length() > 1) {
            // Remove common suffixes that might appear in stock symbols
            String[] suffixes = {".US", "-US", ".L", ".TO", "-A", "-B", ".A", ".B", ".PR", ".PF"};
            String cleanSymbol = symbol;
            for (String suffix : suffixes) {
                if (cleanSymbol.endsWith(suffix)) {
                    cleanSymbol = cleanSymbol.substring(0, cleanSymbol.length() - suffix.length());
                    break;
                }
            }
            return cleanSymbol; // Return the cleaned symbol as a fallback
        }
        
        return symbol; // Return the original symbol if all else fails
    }
    
    /**
     * Check if text contains positive keywords
     */
    private boolean containsPositiveKeywords(String text) {
        String[] keywords = {
            // Basic positive terms
            "up", "rise", "gain", "positive", "strong", "growth", "profit", "success",
            // Expanded financial positive terms
            "outperform", "beat", "exceed", "upgrade", "bullish", "rally", "surge", "soar", 
            "jump", "boost", "upside", "opportunity", "recovery", "breakthrough", "momentum", 
            "optimistic", "promising", "favorable", "advantage", "strength", "performance",
            // Specific financial positive phrases
            "above consensus", "buy rating", "price target increase", "new high", "dividend increase",
            "beat earnings", "revenue growth", "market leader", "cost reduction", "synergies",
            "strategic acquisition", "expansion", "innovation", "improved guidance",
            // Additional terms
            "biggest bargain", "investing aggressively", "all-time high", "stock rise", "stocks rise",
            // Modern terms
            "AI", "artificial intelligence", "blockchain", "crypto", "metaverse", "cloud computing",
            "digital transformation", "e-commerce", "streaming", "subscription", "recurring revenue",
            "market share", "competitive advantage", "moat", "scalable", "disruptive", "innovative",
            "partnership", "collaboration", "integration", "acquisition", "merger", "deal",
            "expansion", "growth", "scale", "efficiency", "productivity", "automation"
        };
        
        text = text.toLowerCase();
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                log.debug("Found positive keyword: '{}' in text", keyword);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if text contains negative keywords
     */
    private boolean containsNegativeKeywords(String text) {
        String[] keywords = {
            // Basic negative terms
            "down", "fall", "drop", "negative", "weak", "loss", "decline", "miss",
            // Expanded financial negative terms
            "underperform", "downgrade", "bearish", "sink", "plunge", "tumble", "slip", "slump",
            "crash", "recession", "sell-off", "downside", "risk", "concern", "disappoint", "struggle",
            "pressure", "uncertainty", "volatility", "warning", "crisis", "challenge", "headwind",
            // Specific financial negative phrases
            "below estimates", "sell rating", "price target cut", "new low", "dividend cut",
            "missed earnings", "revenue decline", "competitive pressure", "cost increase", "debt",
            "restructuring", "layoffs", "downtime", "investigation", "lawsuit", "recall",
            "regulatory issue", "delayed", "lowered guidance", "margin pressure",
            // Additional terms
            "crushing", "tariffs", "export controls", "falling", "trading lower", "stock down",
            // Modern terms
            "cybersecurity", "data breach", "privacy", "regulation", "compliance", "fine",
            "penalty", "investigation", "lawsuit", "class action", "settlement", "violation",
            "hack", "outage", "downtime", "disruption", "supply chain", "shortage", "inflation",
            "interest rates", "rate hike", "recession", "slowdown", "downturn", "correction",
            "bubble", "overvalued", "valuation", "expensive", "premium", "competition",
            "market share loss", "subscriber loss", "user decline", "engagement drop"
        };
        
        text = text.toLowerCase();
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                log.debug("Found negative keyword: '{}' in text", keyword);
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("error", message);
        result.put("sentiment", "neutral");
        result.put("positiveCount", 0);
        result.put("negativeCount", 0);
        result.put("neutralCount", 0);
        result.put("totalArticles", 0);
        result.put("score", 0.0);
        result.put("analyzedArticles", new ArrayList<>());
        return result;
    }

    private boolean isRelevantToStock(String title, String summary, String symbol, String companyName) {
        if (title == null || title.isEmpty()) {
            log.debug("Relevance check: Skipping article with empty title.");
            return false;
        }
        
        String textToCheck = (title + " " + (summary != null ? summary : "")).toLowerCase();
        String symbolLower = symbol.toLowerCase();
        String companyLower = (companyName != null && !companyName.isEmpty()) ? companyName.toLowerCase() : "";
        
        // Primary Check: Direct mention of symbol or company name
        boolean containsSymbol = textToCheck.contains(symbolLower);
        boolean containsCompany = !companyLower.isEmpty() && textToCheck.contains(companyLower);
        boolean isDirectlyRelevant = containsSymbol || containsCompany;
        
        log.debug("Relevance Check:");
        log.debug("  Symbol: '{}', Company: '{}'", symbol, companyName);
        log.debug("  Title: '{}'", title);
        log.debug("  Summary: '{}'", summary != null ? summary : "N/A");
        log.debug("  Text Checked (lower): '{}'", textToCheck);
        log.debug("  Contains Symbol ('{}'): {}", symbolLower, containsSymbol);
        log.debug("  Contains Company ('{}'): {}", companyLower, containsCompany);
        log.debug("  Directly Relevant: {}", isDirectlyRelevant);

        if (isDirectlyRelevant) {
            log.debug("  Result: Relevant (Direct Match)");
            return true;
        }
        
        // Secondary Check: Check for common financial terms if no direct match
        String[] financialTerms = {
            "stock", "shares", "market", "trading", "price", "investor", 
            "earnings", "revenue", "profit", "growth", "performance",
            "dividend", "analyst", "target price", "valuation", "market cap",
            symbolLower // Also include the symbol itself in terms check
        };
        
        int financialTermCount = 0;
        for (String term : financialTerms) {
            if (textToCheck.contains(term)) {
                financialTermCount++;
            }
        }
        
        boolean isIndirectlyRelevant = financialTermCount >= 2; // Require at least 2 terms
        log.debug("  Financial Terms Found: {}", financialTermCount);
        log.debug("  Indirectly Relevant (>=2 terms): {}", isIndirectlyRelevant);
        log.debug("  Result: {}", isIndirectlyRelevant ? "Relevant (Indirect Match)" : "Irrelevant");

        return isIndirectlyRelevant;
    }
}