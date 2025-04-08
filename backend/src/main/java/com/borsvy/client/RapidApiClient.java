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
    public List<NewsArticle> getStockNews(String symbol, int limit) {
        // Try direct news API first
        List<NewsArticle> articles = getNewsViaNewsAPI(symbol, limit);
        return articles;
    }
    
    /**
     * Gets news sentiment analysis for a stock
     */
    public Map<String, Object> getNewsSentiment(String symbol) {
        try (AsyncHttpClient client = new DefaultAsyncHttpClient()) {
            String url = "https://yahoo-finance15.p.rapidapi.com/api/v1/markets/news?tickers=" + symbol;
            log.info("Making sentiment request to URL: {}", url);
            
            return client.prepare("GET", url)
                .setHeader("x-rapidapi-key", apiKey)
                .setHeader("x-rapidapi-host", apiHost)
                .execute()
                .toCompletableFuture()
                .thenApply(response -> {
                    try {
                        if (response.getStatusCode() != 200) {
                            return createErrorResponse("No news sentiment data available");
                        }
                        
                        JsonNode root = objectMapper.readTree(response.getResponseBody());
                        if (!root.has("body") || !root.get("body").isArray()) {
                            return createErrorResponse("No news data available");
                        }
                        
                        JsonNode newsArray = root.get("body");
                        int positiveCount = 0;
                        int negativeCount = 0;
                        int neutralCount = 0;
                        List<Map<String, Object>> analyzedArticles = new ArrayList<>();
                        
                        for (JsonNode article : newsArray) {
                            String title = article.has("title") ? article.get("title").asText("") : "";
                            String summary = article.has("text") ? article.get("text").asText("") : "";
                            
                            // Analyze both title and summary
                            boolean isPositive = containsPositiveKeywords(title + " " + summary);
                            boolean isNegative = containsNegativeKeywords(title + " " + summary);
                            
                            Map<String, Object> analyzedArticle = new HashMap<>();
                            analyzedArticle.put("title", title);
                            analyzedArticle.put("sentiment", isPositive ? "positive" : isNegative ? "negative" : "neutral");
                            
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
                        
                        // Calculate sentiment score
                        double score = (double)(positiveCount - negativeCount) / total;
                        String overallSentiment;
                        if (score > 0.3) overallSentiment = "bullish";
                        else if (score > 0.1) overallSentiment = "slightly bullish";
                        else if (score < -0.3) overallSentiment = "bearish";
                        else if (score < -0.1) overallSentiment = "slightly bearish";
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
     * Gets news via search API - more relevant but might return fewer results
     */
    private List<NewsArticle> getNewsViaSearch(String symbol, int limit) {
        final String companyName = getCompanyNameForSymbol(symbol);
        final String searchQuery = !companyName.isEmpty() 
            ? companyName + " " + symbol + " stock financial news" 
            : symbol + " stock financial news";
        
        try (AsyncHttpClient client = new DefaultAsyncHttpClient()) {
            String url = "https://yahoo-finance15.p.rapidapi.com/api/v1/search?search=" + searchQuery + "&lang=en";
            log.info("Making search request to URL: {}", url);
            
            return client.prepare("GET", url)
                .setHeader("x-rapidapi-key", apiKey)
                .setHeader("x-rapidapi-host", apiHost)
                .execute()
                .toCompletableFuture()
                .<List<NewsArticle>>thenApply(response -> {
                    try {
                        if (response.getStatusCode() != 200) {
                            log.error("Search API error: {}", response.getResponseBody());
                            return new ArrayList<>();
                        }
                        
                        JsonNode root = objectMapper.readTree(response.getResponseBody());
                        List<NewsArticle> articles = new ArrayList<>();
                        
                        // Extract news from quotes section
                        if (root.has("quotes") && root.get("quotes").isArray()) {
                            JsonNode quotes = root.get("quotes");
                            log.info("Found {} search results", quotes.size());
                            
                            for (JsonNode quote : quotes) {
                                if (quote.has("news") && quote.get("news").isArray()) {
                                    for (JsonNode item : quote.get("news")) {
                                        if (articles.size() >= limit) break;
                                        NewsArticle article = extractArticle(item);
                                        if (article != null) {
                                            articles.add(article);
                                            log.info("Added search article: {}", article.getTitle());
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Also check news section if available
                        if (articles.size() < limit && root.has("news") && root.get("news").isArray()) {
                            for (JsonNode item : root.get("news")) {
                                if (articles.size() >= limit) break;
                                NewsArticle article = extractArticle(item);
                                if (article != null && !isDuplicate(articles, article)) {
                                    articles.add(article);
                                    log.info("Added news section article: {}", article.getTitle());
                                }
                            }
                        }
                        
                        return articles;
                    } catch (Exception e) {
                        log.error("Error processing search: {}", e.getMessage());
                        return new ArrayList<>();
                    }
                })
                .exceptionally(t -> {
                    log.error("Exception in search: {}", t.getMessage());
                    return new ArrayList<>();
                })
                .join();
        } catch (Exception e) {
            log.error("Error in search: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Gets news via direct news API - might be less relevant but returns more results
     */
    private List<NewsArticle> getNewsViaNewsAPI(String symbol, int limit) {
        if (limit <= 0) return new ArrayList<>();
        final String companyName = getCompanyNameForSymbol(symbol);
        
        try (AsyncHttpClient client = new DefaultAsyncHttpClient()) {
            // Use a more specific search query that includes both symbol and company name
            String searchQuery = symbol;
            if (!companyName.isEmpty()) {
                searchQuery += " OR " + companyName;
            }
            
            String url = "https://yahoo-finance15.p.rapidapi.com/api/v1/markets/news?tickers=" + searchQuery;
            log.info("Making news request to URL: {}", url);
            
            return client.prepare("GET", url)
                .setHeader("x-rapidapi-key", apiKey)
                .setHeader("x-rapidapi-host", apiHost)
                .execute()
                .toCompletableFuture()
                .<List<NewsArticle>>thenApply(response -> {
                    try {
                        if (response.getStatusCode() != 200) {
                            log.error("News API error: {}", response.getResponseBody());
                            return new ArrayList<>();
                        }
                        
                        String responseBody = response.getResponseBody();
                        log.debug("Raw API response: {}", responseBody);
                        
                        JsonNode root = objectMapper.readTree(responseBody);
                        List<NewsArticle> articles = new ArrayList<>();
                        
                        // Try different possible response structures
                        JsonNode newsArray = null;
                        if (root.has("body") && root.get("body").isArray()) {
                            newsArray = root.get("body");
                            log.info("Found news articles in 'body' array");
                        } else if (root.has("data") && root.get("data").isArray()) {
                            newsArray = root.get("data");
                            log.info("Found news articles in 'data' array");
                        } else if (root.isArray()) {
                            newsArray = root;
                            log.info("Found news articles in root array");
                        }
                        
                        if (newsArray != null) {
                            log.info("Found {} news articles in response", newsArray.size());
                            
                            for (JsonNode item : newsArray) {
                                if (articles.size() >= limit) break;
                                
                                // Check if the article is relevant to the stock
                                String title = item.has("title") ? item.get("title").asText("") : "";
                                
                                // Only require title to be non-empty
                                if (title.isEmpty()) {
                                    log.debug("Skipping article with empty title");
                                    continue;
                                }
                                
                                // Check relevance
                                if (isHighlyRelevant(title, symbol, companyName)) {
                                    NewsArticle article = extractArticle(item);
                                    if (article != null) {
                                        articles.add(article);
                                        log.info("Added relevant article: {} with thumbnail: {}", 
                                            article.getTitle(), article.getThumbnail());
                                    }
                                } else {
                                    log.debug("Skipping irrelevant article: {}", title);
                                }
                            }
                        } else {
                            log.warn("No news array found in API response. Response structure: {}", 
                                root.toString().substring(0, Math.min(500, root.toString().length())));
                        }
                        
                        // If we didn't get enough articles, try the search API as fallback
                        if (articles.size() < limit) {
                            log.info("Not enough articles from news API, trying search API as fallback");
                            List<NewsArticle> searchArticles = getNewsViaSearch(symbol, limit - articles.size());
                            articles.addAll(searchArticles);
                        }
                        
                        return articles;
                    } catch (Exception e) {
                        log.error("Error processing news: {} - {}", e.getMessage(), e.getClass().getName());
                        return new ArrayList<>();
                    }
                })
                .exceptionally(t -> {
                    log.error("Exception in news: {}", t.getMessage());
                    return new ArrayList<>();
                })
                .join();
        } catch (Exception e) {
            log.error("Error in news API: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Extract article data from JSON node
     */
    private NewsArticle extractArticle(JsonNode item) {
        try {
            log.debug("Processing news item: {}", item.toString());
            
            // Extract required fields
            String title = item.has("title") ? item.get("title").asText("") : "";
            if (title.isEmpty()) {
                log.debug("Skipping article with empty title");
                return null;
            }
            
            String link = item.has("link") ? item.get("link").asText("") : "";
            if (link.isEmpty()) {
                log.debug("Skipping article with empty link");
                return null;
            }
            
            // Create article
            NewsArticle article = new NewsArticle();
            article.setTitle(title);
            article.setUrl(link);
            
            // Publisher/source
            String source = item.has("source") ? item.get("source").asText("") : "Yahoo Finance";
            article.setSource(source);
            
            // Date
            String pubDate = item.has("pubDate") ? item.get("pubDate").asText("") : "";
            article.setPublishedDate(formatDate(pubDate));
            
            // Summary - use title if no summary available
            String summary = item.has("text") ? item.get("text").asText("") : title;
            article.setSummary(summary);
            
            // Enhanced thumbnail handling
            String thumbnail = null;
            
            // Try different possible thumbnail fields
            if (item.has("img") && !item.get("img").asText("").isEmpty()) {
                thumbnail = item.get("img").asText("");
            } else if (item.has("image_url") && !item.get("image_url").asText("").isEmpty()) {
                thumbnail = item.get("image_url").asText("");
            } else if (item.has("thumbnail") && !item.get("thumbnail").asText("").isEmpty()) {
                thumbnail = item.get("thumbnail").asText("");
            } else if (item.has("image") && !item.get("image").asText("").isEmpty()) {
                thumbnail = item.get("image").asText("");
            }
            
            // If no thumbnail found, try to extract from the article URL
            if (thumbnail == null || thumbnail.isEmpty()) {
                try {
                    thumbnail = extractThumbnailFromUrl(link);
                } catch (Exception e) {
                    log.debug("Failed to extract thumbnail from URL: {}", e.getMessage());
                }
            }
            
            // Set default thumbnail based on source if no thumbnail found
            if (thumbnail == null || thumbnail.isEmpty()) {
                thumbnail = getDefaultThumbnailForSource(source);
                log.debug("Using default thumbnail for source: {}", source);
            }
            
            article.setThumbnail(thumbnail);
            log.debug("Created article: {} with thumbnail: {}", title, thumbnail);
            
            return article;
        } catch (Exception e) {
            log.error("Error extracting article: {}", e.getMessage());
            return null;
        }
    }
    
    private String extractThumbnailFromUrl(String url) {
        try {
            // Try to get the article's main image using a simple HTML parser
            Document doc = Jsoup.connect(url).get();
            Element metaImage = doc.select("meta[property=og:image]").first();
            if (metaImage != null) {
                return metaImage.attr("content");
            }
            
            // Try to find the first large image in the article
            Element img = doc.select("img[src~=(?i)\\.(png|jpe?g|gif)]").first();
            if (img != null) {
                return img.attr("src");
            }
        } catch (Exception e) {
            log.debug("Failed to extract thumbnail from URL {}: {}", url, e.getMessage());
        }
        return null;
    }
    
    /**
     * Extract article data from v2 API response
     */
    private NewsArticle extractArticleV2(JsonNode item) {
        try {
            log.debug("Processing news item: {}", item.toString());
            
            // Extract required fields
            String title = item.has("title") ? item.get("title").asText("") : "";
            if (title.isEmpty()) return null;
            
            String link = item.has("link") ? item.get("link").asText("") : 
                         item.has("url") ? item.get("url").asText("") : "";
            if (link.isEmpty()) return null;
            
            // Create article
            NewsArticle article = new NewsArticle();
            article.setTitle(title);
            article.setUrl(link);
            
            // Publisher/source
            String source = item.has("source") ? item.get("source").asText("") : 
                          item.has("publisher") ? item.get("publisher").asText("") : "Yahoo Finance";
            article.setSource(source);
            
            // Date
            String pubDate = item.has("pubDate") ? item.get("pubDate").asText("") :
                           item.has("published") ? item.get("published").asText("") :
                           item.has("date") ? item.get("date").asText("") : "";
            article.setPublishedDate(formatDate(pubDate));
            
            // Summary
            String summary = item.has("description") ? item.get("description").asText("") : 
                           item.has("content") ? item.get("content").asText("") :
                           item.has("summary") ? item.get("summary").asText("") : "No summary available";
            article.setSummary(summary);
            
            // Enhanced thumbnail handling for v2
            String thumbnail = null;
            
            // Log all available fields to help debug thumbnail issues
            StringBuilder fields = new StringBuilder();
            Iterator<String> fieldNames = item.fieldNames();
            while (fieldNames.hasNext()) {
                fields.append(fieldNames.next()).append(", ");
            }
            log.debug("Available fields in news item: {}", fields.toString());
            
            // Try different possible thumbnail fields in v2 response
            if (item.has("thumbnail") && !item.get("thumbnail").asText("").isEmpty()) {
                thumbnail = item.get("thumbnail").asText("");
                log.debug("Found thumbnail in 'thumbnail' field: {}", thumbnail);
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
        // Map of source-specific thumbnails with multiple options per source
        Map<String, String[]> sourceThumbnails = new HashMap<>();
        
        // Reuters thumbnails
        sourceThumbnails.put("Reuters", new String[] {
            "https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=800&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1590283603385-17ffb3a7f29f?w=800&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1468254095679-bbcba94a7066?w=800&auto=format&fit=crop"
        });
        
        // Bloomberg thumbnails
        sourceThumbnails.put("Bloomberg", new String[] {
            "https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=800&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1590283603385-17ffb3a7f29f?w=800&auto=format&fit=crop"
        });
        
        // Barrons thumbnails
        sourceThumbnails.put("Barrons", new String[] {
            "https://images.unsplash.com/photo-1590283603385-17ffb3a7f29f?w=800&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1468254095679-bbcba94a7066?w=800&auto=format&fit=crop"
        });
        
        // CNBC thumbnails
        sourceThumbnails.put("CNBC", new String[] {
            "https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=800&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1590283603385-17ffb3a7f29f?w=800&auto=format&fit=crop"
        });
        
        // Insider Monkey thumbnails
        sourceThumbnails.put("Insider Monkey", new String[] {
            "https://images.unsplash.com/photo-1468254095679-bbcba94a7066?w=800&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1590283603385-17ffb3a7f29f?w=800&auto=format&fit=crop"
        });
        
        // Yahoo Finance thumbnails
        sourceThumbnails.put("Yahoo Finance", new String[] {
            "https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=800&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1590283603385-17ffb3a7f29f?w=800&auto=format&fit=crop"
        });
        
        // Try to find a source-specific thumbnail
        for (Map.Entry<String, String[]> entry : sourceThumbnails.entrySet()) {
            if (source.toLowerCase().contains(entry.getKey().toLowerCase())) {
                String[] thumbnails = entry.getValue();
                return thumbnails[new Random().nextInt(thumbnails.length)];
            }
        }
        
        // If no source-specific thumbnail found, use a random default
        return getDefaultFinancialThumbnail();
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
        title = title.toLowerCase();
        return title.contains(symbol.toLowerCase()) || 
               (!companyName.isEmpty() && title.contains(companyName.toLowerCase())) ||
               isProductRelevant(title, symbol);
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
        String[] thumbnails = {
            "https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=800&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1590283603385-17ffb3a7f29f?w=800&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1468254095679-bbcba94a7066?w=800&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1526304640581-d334cdbbf45e?w=800&auto=format&fit=crop"
        };
        
        return thumbnails[new Random().nextInt(thumbnails.length)];
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
        String[] keywords = {"up", "rise", "gain", "positive", "strong", "growth", "profit", "success"};
        text = text.toLowerCase();
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }
    
    /**
     * Check if text contains negative keywords
     */
    private boolean containsNegativeKeywords(String text) {
        String[] keywords = {"down", "fall", "drop", "negative", "weak", "loss", "decline", "miss"};
        text = text.toLowerCase();
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
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
}