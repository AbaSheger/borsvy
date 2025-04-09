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
    public List<NewsArticle> getStockNews(String symbol, int limit) {
        // Use the updated news API method
        return getNewsViaNewsAPI(symbol, limit > 0 ? limit : DEFAULT_LIMIT);
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
     * Gets news via the specific symbol news API
     */
    private List<NewsArticle> getNewsViaNewsAPI(String symbol, int limit) {
        if (limit <= 0) return new ArrayList<>();
        if (symbol == null || symbol.isEmpty()) {
            log.warn("Cannot fetch news without a stock symbol.");
            return new ArrayList<>();
        }
        final String companyName = getCompanyNameForSymbol(symbol);
        
        try (AsyncHttpClient client = Dsl.asyncHttpClient()) { // Use Dsl for simplicity
            String url = "https://yahoo-finance166.p.rapidapi.com/api/news/list-by-symbol";
            log.info("Making news request to URL: {} with symbol: {}, limit: {}", url, symbol, limit);
            
            return client.prepareGet(url)
                .addQueryParam("s", symbol)
                .addQueryParam("region", "US")
                .addQueryParam("snippetCount", String.valueOf(limit)) // Use limit for snippetCount
                .setHeader("x-rapidapi-key", apiKey)
                .setHeader("x-rapidapi-host", "yahoo-finance166.p.rapidapi.com") // Correct host
                .execute()
                .toCompletableFuture()
                .<List<NewsArticle>>thenApply(response -> {
                    try {
                        if (response.getStatusCode() != 200) {
                            log.error("News API error {}: {}", response.getStatusCode(), response.getResponseBody());
                            return new ArrayList<>();
                        }
                        
                        String responseBody = response.getResponseBody();
                        log.debug("Raw API response from yahoo-finance166: {}", responseBody);
                        
                        JsonNode root = objectMapper.readTree(responseBody);
                        List<NewsArticle> articles = new ArrayList<>();
                        
                        // Determine where the news items are located in the response
                        JsonNode newsArray = null;
                        if (root.isArray()) {
                            newsArray = root;
                            log.info("Found news items in root array.");
                        } else if (root.has("items") && root.get("items").isArray()) {
                            newsArray = root.get("items");
                            log.info("Found news items in 'items' array.");
                        } else if (root.has("news") && root.get("news").isArray()) {
                            newsArray = root.get("news");
                            log.info("Found news items in 'news' array.");
                        } else if (root.has("data") && root.get("data").isArray()) {
                            newsArray = root.get("data");
                            log.info("Found news items in 'data' array.");
                        } else {
                            // Log the structure if unfamiliar
                            log.warn("Unexpected JSON structure from news API. Root keys: {}", 
                                     StreamSupport.stream(Spliterators.spliteratorUnknownSize(root.fieldNames(), Spliterator.ORDERED), false)
                                                   .collect(Collectors.joining(", ")));
                        }
                        
                        if (newsArray != null) {
                            log.info("Found {} potential news articles in response.", newsArray.size());
                            
                            int addedCount = 0;
                            for (JsonNode item : newsArray) {
                                if (addedCount >= limit) break;
                                
                                // Use extractArticleV2 as it handles more fields
                                NewsArticle article = extractArticleV2(item);
                                
                                if (article != null) {
                                    // Check relevance using the existing method
                                    if (isRelevantToStock(article.getTitle(), article.getSummary(), symbol, companyName)) {
                                        if (!isDuplicate(articles, article)) {
                                            articles.add(article);
                                            addedCount++;
                                            log.info("Added relevant article ({}): {}", addedCount, article.getTitle());
                                        } else {
                                            log.debug("Skipping duplicate article: {}", article.getTitle());
                                        }
                                    } else {
                                        log.debug("Skipping irrelevant article: {}", article.getTitle());
                                    }
                                } else {
                                    log.warn("Failed to extract article from item: {}", item.toString());
                                }
                            }
                            log.info("Finished processing. Added {} relevant articles.", addedCount);
                        } else {
                            log.warn("No parsable news array found in API response.");
                        }
                        
                        return articles;
                    } catch (Exception e) {
                        log.error("Error processing news from yahoo-finance166: {} - {}", e.getMessage(), e.getClass().getName(), e);
                        return new ArrayList<>();
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