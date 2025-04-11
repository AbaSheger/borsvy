package com.borsvy.service;

import com.borsvy.model.Stock;
import com.borsvy.model.StockPrice;
import com.borsvy.model.StockDetails;
import com.borsvy.repository.StockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.borsvy.client.FinnhubClient;
import com.borsvy.client.SerpApiClient;
import com.borsvy.model.Quote;
import com.borsvy.model.CompanyProfile2;
import lombok.extern.slf4j.Slf4j;
import com.borsvy.client.PolygonClient;
import com.borsvy.client.RapidApiClient;
import com.borsvy.model.NewsArticle;

import java.util.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.time.Duration;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StockService {

    private final StockRepository stockRepository;
    private final FinnhubClient finnhubClient;
    private final SerpApiClient serpApiClient;
    private final PolygonClient polygonClient;
    private final RapidApiClient rapidApiClient;
    private final NewsAnalysisService newsAnalysisService; // Changed to interface instead of implementation
    private final Map<String, CachedStock> stockCache = new ConcurrentHashMap<>();
    private final Map<String, CachedStockDetails> detailsCache = new ConcurrentHashMap<>();
    private List<Stock> cachedPopularStocks;
    private long popularStocksCacheTime;
    private final List<String> popularStocks = Arrays.asList("AAPL", "MSFT", "GOOGL", "AMZN", "META", "NVDA", "TSLA", "JPM", "V", "WMT");
    
    private static final int CACHE_EXPIRY_MINUTES = 1;
    private static final int DETAILS_CACHE_EXPIRY_HOURS = 1;

    @Autowired
    public StockService(StockRepository stockRepository, 
                       FinnhubClient finnhubClient, 
                       SerpApiClient serpApiClient,
                       PolygonClient polygonClient,
                       RapidApiClient rapidApiClient,
                       NewsAnalysisService newsAnalysisService) {
        this.stockRepository = stockRepository;
        this.finnhubClient = finnhubClient;
        this.serpApiClient = serpApiClient;
        this.polygonClient = polygonClient;
        this.rapidApiClient = rapidApiClient;
        this.newsAnalysisService = newsAnalysisService;
    }

    @Retryable(
        value = { Exception.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    private Stock fetchStockFromFinnhub(String symbol) throws IOException {
        try {
            // Check cache first
            CachedStock cached = stockCache.get(symbol);
            if (cached != null && !cached.isExpired()) {
                log.debug("Returning cached data for {}", symbol);
                return cached.stock;
            }

            // Check database before making API call
            Optional<Stock> dbStock = stockRepository.findById(symbol);
            if (dbStock.isPresent() && 
                dbStock.get().getLastUpdated() != null && 
                Duration.between(dbStock.get().getLastUpdated(), LocalDateTime.now()).toMinutes() < CACHE_EXPIRY_MINUTES) {
                log.debug("Returning database data for {}", symbol);
                Stock stock = dbStock.get();
                stockCache.put(symbol, new CachedStock(stock));
                return stock;
            }

            // If not in cache or database, fetch from Finnhub
            Quote quote = finnhubClient.getQuote(symbol);
            if (quote == null) {
                return null;
            }

            // Create or update stock object
            Stock stock = dbStock.orElse(new Stock());
            stock.setSymbol(symbol);
            
            try {
                stock.setPrice(quote.getCurrentPrice());
                stock.setChange(quote.getChange());
                stock.setChangePercent(quote.getPercentChange());
                stock.setHigh(quote.getHigh());
                stock.setLow(quote.getLow());
                stock.setOpen(quote.getOpen());
                stock.setVolume(quote.getVolume());
            } catch (Exception e) {
                log.error("Error parsing numeric values from Finnhub response for symbol {}: {}", symbol, e.getMessage());
                return null;
            }

            // Get company profile for additional details
            try {
                CompanyProfile2 profile = finnhubClient.getCompanyProfile2(symbol);
                if (profile != null) {
                    stock.setName(profile.getName());
                    stock.setIndustry(profile.getFinnhubIndustry());
                    stock.setMarketCap(profile.getMarketCapitalization());
                    stock.setBeta(profile.getBeta());
                    Double peRatio = profile.getPe();
                    log.debug("P/E ratio from Finnhub API for {}: {}", symbol, peRatio);
                    stock.setPeRatio(peRatio);
                }
            } catch (Exception e) {
                log.warn("Could not fetch company profile for {}: {}", symbol, e.getMessage());
            }

            stock.setLastUpdated(LocalDateTime.now());

            // Save to database and update cache
            stock = stockRepository.save(stock);
            stockCache.put(symbol, new CachedStock(stock));
            
            return stock;

        } catch (Exception e) {
            log.error("Error fetching stock data from Finnhub: {}", e.getMessage());
            throw e;
        }
    }

    @Retryable(
        value = { Exception.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public StockDetails getStockDetails(String symbol) throws IOException {
        try {
            // Check cache first
            CachedStockDetails cached = detailsCache.get(symbol);
            if (cached != null && !cached.isExpired()) {
                return cached.details;
            }

            // Get real-time data from Finnhub
            Quote quote = finnhubClient.getQuote(symbol);
            CompanyProfile2 profile = finnhubClient.getCompanyProfile2(symbol);

            if (quote == null) {
                log.error("Failed to fetch quote data for symbol: {}", symbol);
                throw new IOException("Failed to fetch quote data from Finnhub API");
            }

            StockDetails details = new StockDetails();
            details.setSymbol(symbol);
            
            // Set basic quote data
            details.setPrice(quote.getCurrentPrice());
            details.setChange(quote.getChange());
            details.setChangePercent(quote.getPercentChange());
            details.setHigh(quote.getHigh());
            details.setLow(quote.getLow());
            details.setOpen(quote.getOpen());
            details.setPreviousClose(quote.getOpen() - quote.getChange());
            details.setVolume(quote.getVolume());

            // Set profile data if available
            if (profile != null) {
                details.setName(profile.getName());
                details.setIndustry(profile.getFinnhubIndustry());
                
                // Market cap is in billions from the API
                double marketCapInBillions = profile.getMarketCapitalization();
                if (marketCapInBillions > 0) {
                    details.setMarketCap(marketCapInBillions);
                }
                
                // Set fundamental data
                details.setPeRatio(profile.getPe());
                details.setBeta(profile.getBeta());
            }

            // Cache the details
            detailsCache.put(symbol, new CachedStockDetails(details));
            return details;

        } catch (Exception e) {
            log.error("Error fetching stock details for {}: {}", symbol, e.getMessage());
            throw new IOException("Failed to fetch stock details: " + e.getMessage());
        }
    }

    public List<Stock> searchStocks(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String normalizedQuery = query.trim().toUpperCase();
        List<Stock> searchResults = new ArrayList<>();
        
        try {
            // First check cache
            CachedStock cached = stockCache.get(normalizedQuery);
            if (cached != null && !cached.isExpired()) {
                log.debug("Returning cached search result for {}", normalizedQuery);
                searchResults.add(cached.stock);
                return searchResults;
            }
            
            // Then check database
            List<Stock> dbResults = stockRepository.findBySymbolContainingIgnoreCaseOrNameContainingIgnoreCase(
                normalizedQuery, normalizedQuery);
            if (!dbResults.isEmpty()) {
                // Update cache for each result
                for (Stock stock : dbResults) {
                    if (stock.getLastUpdated() != null && 
                        Duration.between(stock.getLastUpdated(), LocalDateTime.now()).toMinutes() < CACHE_EXPIRY_MINUTES) {
                        stockCache.put(stock.getSymbol(), new CachedStock(stock));
                        searchResults.add(stock);
                    }
                }
                if (!searchResults.isEmpty()) {
                    return searchResults;
                }
            }
            
            // If no valid results in cache or database, try API
            Stock stock = fetchStockFromFinnhub(normalizedQuery);
            if (stock != null) {
                searchResults.add(stock);
            }
            
            return searchResults;
        } catch (Exception e) {
            log.error("Error searching stocks: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public Optional<Stock> getStockBySymbol(String symbol) {
        try {
            log.debug("Fetching stock with symbol: {}", symbol);
            
            // Validate symbol
            if (symbol == null || symbol.trim().isEmpty() || symbol.equals("undefined")) {
                log.error("Invalid symbol provided: {}", symbol);
                return Optional.empty();
            }
            
            // Check cache first
            CachedStock cached = stockCache.get(symbol);
            if (cached != null && !cached.isExpired()) {
                log.debug("Returning cached data for {}", symbol);
                return Optional.of(cached.stock);
            }

            // Check database before making API call
            Optional<Stock> dbStock = stockRepository.findById(symbol);
            if (dbStock.isPresent() && 
                dbStock.get().getLastUpdated() != null && 
                Duration.between(dbStock.get().getLastUpdated(), LocalDateTime.now()).toMinutes() < CACHE_EXPIRY_MINUTES) {
                log.debug("Returning database data for {}", symbol);
                Stock stock = dbStock.get();
                stockCache.put(symbol, new CachedStock(stock));
                return Optional.of(stock);
            }

            // If not in cache or database, fetch from Finnhub
            Stock stock = fetchStockFromFinnhub(symbol);
            if (stock != null) {
                return Optional.of(stock);
            }
            
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error fetching stock {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    public List<Stock> getPopularStocks() {
        // Return cached popular stocks if not expired
        if (cachedPopularStocks != null && System.currentTimeMillis() - popularStocksCacheTime < 3600000) {
            return cachedPopularStocks;
        }
        
        // Load from database only
        List<Stock> dbStocks = stockRepository.findAllById(popularStocks);
        if (!dbStocks.isEmpty()) {
            cachedPopularStocks = dbStocks;
            popularStocksCacheTime = System.currentTimeMillis();
            return dbStocks;
        }
        
        // If no data in database, return empty list
        return new ArrayList<>();
    }

    public List<StockPrice> getHistoricalData(String symbol, String interval) {
        // Try to get data from Polygon.io
        List<StockPrice> polygonData = polygonClient.getHistoricalData(symbol, interval);
        
        if (!polygonData.isEmpty()) {
            log.info("Using Polygon.io data for {} with interval {}", symbol, interval);
            return polygonData;
        }
        
        // If Polygon.io didn't return data, try to get current stock data
        try {
            Stock currentStock = fetchStockFromFinnhub(symbol);
            if (currentStock != null) {
                log.info("Using current stock data to generate historical data for {} with interval {}", symbol, interval);
                return generatePriceHistoryFromCurrentPrice(currentStock, interval);
            }
        } catch (IOException e) {
            log.warn("Failed to fetch current stock data for {}: {}", symbol, e.getMessage());
        }
        
        // If all else fails, generate synthetic data
        log.info("No data available for {} with interval {}, using synthetic data", symbol, interval);
        return generateSyntheticHistoricalData(symbol, interval);
    }
    
    public Map<String, Object> getNewsSentiment(String symbol) {
        try {
            log.info("Getting news sentiment for symbol: {}", symbol);
            
            // First get actual news articles
            List<NewsArticle> newsArticles = getStockNews(symbol, 15); // Get up to 15 articles for analysis
            
            if (newsArticles.isEmpty()) {
                log.warn("No news articles found for sentiment analysis");
                Map<String, Object> noNewsResult = new HashMap<>();
                noNewsResult.put("sentiment", "neutral");
                noNewsResult.put("confidence", 0.5);
                noNewsResult.put("error", "No news articles available for sentiment analysis");
                return noNewsResult;
            }
            
            log.info("Found {} news articles for sentiment analysis", newsArticles.size());
            
            // Use LLM service to analyze sentiment based on actual news articles
            Map<String, Object> sentimentResults = newsAnalysisService.analyzeNewsSentiment(symbol, newsArticles);
            log.info("LLM analysis complete: sentiment={}, confidence={}", 
                    sentimentResults.get("sentiment"), sentimentResults.get("confidence"));
            
            return sentimentResults;
            
        } catch (Exception e) {
            log.error("Error in getNewsSentiment: {}", e.getMessage(), e);
            Map<String, Object> errorSentiment = new HashMap<>();
            errorSentiment.put("error", "Failed to analyze sentiment");
            errorSentiment.put("sentiment", "neutral");
            errorSentiment.put("confidence", 0.5);
            return errorSentiment;
        }
    }
    
    public List<NewsArticle> getStockNews(String symbol, int limit) {
        try {
            log.info("Fetching news for symbol: {} with limit: {}", symbol, limit);
            
            // Try SerpApi first
            boolean serpApiSuccess = false;
            List<Map<String, Object>> serpApiNews = null;
            try {
                log.info("Attempting to use SerpAPI for news...");
                serpApiNews = serpApiClient.getStockNews(symbol, limit);
                
                // Better detection of API limit errors
                if (serpApiNews != null && !serpApiNews.isEmpty()) {
                    // Check if there's an error message that indicates API limit
                    boolean hasApiLimitError = serpApiNews.stream()
                        .anyMatch(article -> article.containsKey("error") && 
                                 article.get("error") != null && 
                                 article.get("error").toString().toLowerCase().contains("limit"));
                    
                    if (!hasApiLimitError) {
                        log.info("Successfully fetched {} news articles from SerpApi", serpApiNews.size());
                        List<NewsArticle> articles = convertToNewsArticles(serpApiNews);
                        if (!articles.isEmpty()) {
                            serpApiSuccess = true;
                            return articles;
                        }
                    } else {
                        log.warn("SerpAPI limit reached. Falling back to RapidApi");
                    }
                } else {
                    log.warn("SerpAPI returned no results. Falling back to RapidApi");
                }
            } catch (Exception e) {
                log.warn("Error fetching news from SerpApi: {}. Falling back to RapidApi", e.getMessage());
            }
            
            // Fallback to RapidApi if SerpApi failed
            if (!serpApiSuccess) {
                try {
                    log.info("Attempting to use RapidAPI for news (fallback)...");
                    List<NewsArticle> rapidApiNews = rapidApiClient.getStockNews(symbol, limit);
                    if (rapidApiNews != null && !rapidApiNews.isEmpty()) {
                        log.info("Successfully fetched {} news articles from RapidApi (fallback)", rapidApiNews.size());
                        return rapidApiNews;
                    } else {
                        log.warn("RapidAPI returned no news results");
                    }
                } catch (Exception e) {
                    log.error("Error fetching news from RapidApi (fallback): {}", e.getMessage());
                }
            }
            
            // If both APIs fail, return empty list
            log.warn("Both news APIs failed, returning empty list");
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Error in getStockNews: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<NewsArticle> convertToNewsArticles(List<Map<String, Object>> articles) {
        List<NewsArticle> newsArticles = new ArrayList<>();
        for (Map<String, Object> article : articles) {
            try {
                NewsArticle newsArticle = new NewsArticle();
                newsArticle.setTitle((String) article.get("title"));
                newsArticle.setUrl((String) article.get("url"));
                newsArticle.setSource((String) article.get("source"));
                newsArticle.setPublishedDate((String) article.get("date"));
                newsArticle.setSummary((String) article.get("summary"));
                newsArticle.setThumbnail((String) article.get("thumbnail"));
                newsArticles.add(newsArticle);
            } catch (Exception e) {
                log.warn("Error converting article: {}", e.getMessage());
            }
        }
        return newsArticles;
    }

    private List<StockPrice> generateSyntheticHistoricalData(String symbol, String interval) {
        Stock dummyStock = createDummyStock(symbol);
        return generatePriceHistoryFromCurrentPrice(dummyStock, interval);
    }

    private List<StockPrice> generatePriceHistoryFromCurrentPrice(Stock stock, String interval) {
        List<StockPrice> priceHistory = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        double currentPrice = stock.getPrice();
        
        int dataPoints;
        ChronoUnit unit;
        
        // Determine number of data points and time unit based on interval
        switch (interval != null ? interval.toLowerCase() : "1d") {
            case "1d":
                dataPoints = 390; // 6.5 hours * 60 minutes
                unit = ChronoUnit.MINUTES;
                break;
            case "1w":
                dataPoints = 168; // 7 days * 24 hours
                unit = ChronoUnit.HOURS;
                break;
            case "1m":
                dataPoints = 30;
                unit = ChronoUnit.DAYS;
                break;
            case "3m":
                dataPoints = 90;
                unit = ChronoUnit.DAYS;
                break;
            case "6m":
                dataPoints = 180;
                unit = ChronoUnit.DAYS;
                break;
            case "1y":
                dataPoints = 252; // Trading days in a year
                unit = ChronoUnit.DAYS;
                break;
            default:
                dataPoints = 30;
                unit = ChronoUnit.DAYS;
        }
        
        // Generate price history with realistic volatility
        double volatility = 0.02; // 2% daily volatility
        double price = currentPrice;
        
        for (int i = dataPoints; i > 0; i--) {
            StockPrice pricePoint = new StockPrice();
            pricePoint.setSymbol(stock.getSymbol());
            pricePoint.setTimestamp(now.minus(i, unit));
            
            // Add some randomness to the price movement
            double randomChange = (ThreadLocalRandom.current().nextDouble() - 0.5) * volatility * price;
            price += randomChange;
            
            // Ensure price stays positive
            price = Math.max(price, 0.01);
            
            pricePoint.setPrice(price);
            pricePoint.setVolume((long)(stock.getVolume() * (1 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.2)));
            priceHistory.add(pricePoint);
        }
        
        return priceHistory;
    }

    public List<Map<String, Object>> getChartData(String symbol, String interval) {
        List<StockPrice> priceHistory = getHistoricalData(symbol, interval);
        
        List<String> labels = new ArrayList<>();
        List<Double> prices = new ArrayList<>();
        List<Long> volumes = new ArrayList<>();
        
        for (StockPrice price : priceHistory) {
            LocalDateTime timestamp = price.getTimestamp();
            // Format timestamp appropriately based on interval
            String formattedDate;
            if (interval != null && (interval.equals("1d") || interval.equals("5d"))) {
                formattedDate = String.format("%02d:%02d", timestamp.getHour(), timestamp.getMinute());
            } else {
                formattedDate = String.format("%d-%02d-%02d", timestamp.getYear(), timestamp.getMonthValue(), timestamp.getDayOfMonth());
            }
            
            labels.add(formattedDate);
            prices.add(price.getPrice());
            volumes.add(price.getVolume());
        }
        
        // Create a single map with all chart data
        Map<String, Object> chartData = new HashMap<>();
        chartData.put("labels", labels);
        chartData.put("prices", prices);
        chartData.put("volumes", volumes);
        
        // Wrap in a list to match controller expectation
        return List.of(chartData);
    }

    public Map<String, Object> getAnalysis(String symbol) {
        try {
            StockDetails details = getStockDetails(symbol);
            if (details == null) {
                throw new IOException("Failed to fetch stock details");
            }

            Map<String, Object> analysis = new HashMap<>();
            
            // Technical Analysis
            StringBuilder technical = new StringBuilder();
            technical.append(details.getName()).append(" is currently trading at $")
                    .append(String.format(Locale.US, "%.2f", details.getPrice()))
                    .append(" (").append(String.format(Locale.US, "%.2f", details.getChangePercent())).append("%)");
            
            if (details.getVolume() != null && details.getVolume() > 0) {
                technical.append(", with a volume of ").append(formatNumber(details.getVolume())).append(" shares");
            }
            
            if (details.getHigh() != null && details.getLow() != null) {
                technical.append(". The stock has been trading between $")
                        .append(String.format(Locale.US, "%.2f", details.getLow()))
                        .append(" and $")
                        .append(String.format(Locale.US, "%.2f", details.getHigh()))
                        .append(" today.");
            }
            
            // Add technical indicators if we have stock data in the database
            Optional<Stock> stockData = getStockBySymbol(symbol);
            if (stockData.isPresent()) {
                Stock stock = stockData.get();
                if (stock.getHigh52Week() > 0 && stock.getLow52Week() > 0) {
                    technical.append("\n\nKey Technical Indicators:\n");
                    technical.append(generateTechnicalIndicators(stock));
                }
            }
            
            analysis.put("technical", technical.toString());

            // Fundamental Analysis
            StringBuilder fundamental = new StringBuilder();
            fundamental.append(details.getName())
                    .append(" is a company in the ")
                    .append(details.getIndustry())
                    .append(" industry");
            
            if (details.getMarketCap() != null) {
                fundamental.append(" with a market capitalization of ")
                        .append(formatMarketCap(details.getMarketCap()));
            }
            
            if (details.getPeRatio() != null && details.getPeRatio() > 0) {
                fundamental.append(". The company has a P/E ratio of ")
                        .append(String.format(Locale.US, "%.2f", details.getPeRatio()));
            }
            
            analysis.put("fundamental", fundamental.toString());

            // Market Sentiment
            String sentiment = generateSentiment(
                details.getChange(),
                details.getVolume(),
                null // We don't have average volume in free tier
            );
            analysis.put("sentiment", sentiment);

            // Recommendation
            String recommendation = generateRecommendation(
                details.getChange(),
                details.getPeRatio(),
                details.getBeta(),
                details.getVolume(),
                null // We don't have average volume in free tier
            );
            analysis.put("recommendation", recommendation);

            return analysis;

        } catch (Exception e) {
            log.error("Error generating analysis for {}: {}", symbol, e.getMessage());
            return generateFallbackAnalysis(symbol);
        }
    }

    private Map<String, Object> generateFallbackAnalysis(String symbol) {
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("technical", "Technical analysis data is currently unavailable.");
        analysis.put("fundamental", "Fundamental analysis data is currently unavailable.");
        analysis.put("sentiment", "Neutral");
        analysis.put("recommendation", "Hold");
        return analysis;
    }

    public String generateSentiment(double changePercent, long volume, Long avgVolume) {
        // Consider both price movement and volume
        int signals = 0;
        
        // Price signals
        if (changePercent > 5) signals += 2;
        else if (changePercent > 2) signals += 1;
        else if (changePercent < -5) signals -= 2;
        else if (changePercent < -2) signals -= 1;
        
        // Volume signals
        if (avgVolume != null && avgVolume > 0) {
            double volumeRatio = (double) volume / avgVolume;
            if (volumeRatio > 2.0) signals += (changePercent > 0 ? 2 : -2);
            else if (volumeRatio > 1.5) signals += (changePercent > 0 ? 1 : -1);
        }
        
        // Determine sentiment based on combined signals
        if (signals >= 3) return "Very Bullish";
        if (signals >= 1) return "Bullish";
        if (signals <= -3) return "Very Bearish";
        if (signals <= -1) return "Bearish";
        return "Neutral";
    }

    public String generateRecommendation(double changePercent, double pe, Double beta, long volume, Long avgVolume) {
        int signals = 0;
        
        // Technical signals
        if (changePercent > 5) signals += 2;
        else if (changePercent > 2) signals += 1;
        else if (changePercent < -5) signals -= 2;
        else if (changePercent < -2) signals -= 1;
        
        // Volume signals
        if (avgVolume != null && avgVolume > 0) {
            double volumeRatio = (double) volume / avgVolume;
            if (volumeRatio > 2.0) signals += (changePercent > 0 ? 2 : -2);
            else if (volumeRatio > 1.5) signals += (changePercent > 0 ? 1 : -1);
        }

        // Fundamental signals
        if (pe > 0) {
            if (pe > 30) signals -= 1;
            else if (pe < 15) signals += 1;
        }
        
        if (beta != null && beta > 0) {
            if (beta > 1.5) signals += (changePercent > 0 ? 1 : -1);
            else if (beta < 0.5) signals -= (changePercent > 0 ? 1 : -1);
        }
        
        // Generate recommendation based on combined signals
        if (signals >= 4) return "Strong Buy";
        if (signals >= 2) return "Buy";
        if (signals <= -4) return "Strong Sell";
        if (signals <= -2) return "Sell";
        return "Hold";
    }

    public Stock createDummyStock(String symbol) {
        Stock stock = new Stock();
        stock.setSymbol(symbol);
        stock.setName(symbol);
        stock.setPrice(100.0 + ThreadLocalRandom.current().nextDouble(-10, 10));
        stock.setChange(ThreadLocalRandom.current().nextDouble(-5, 5));
        stock.setChangePercent(ThreadLocalRandom.current().nextDouble(-5, 5));
        stock.setHigh(stock.getPrice() + ThreadLocalRandom.current().nextDouble(0, 5));
        stock.setLow(stock.getPrice() - ThreadLocalRandom.current().nextDouble(0, 5));
        stock.setOpen(stock.getPrice() + ThreadLocalRandom.current().nextDouble(-2, 2));
        stock.setVolume(ThreadLocalRandom.current().nextLong(1000000, 5000000));
        stock.setMarketCap(1000000000.0);
        stock.setPeRatio(20.0);
        stock.setBeta(1.0);
        stock.setLastUpdated(LocalDateTime.now());
        return stock;
    }

    private String formatNumber(long number) {
        if (number >= 1_000_000_000) {
            return String.format(Locale.US, "%.2fB", number / 1_000_000_000.0);
        } else if (number >= 1_000_000) {
            return String.format(Locale.US, "%.2fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format(Locale.US, "%.2fK", number / 1_000.0);
        }
        return String.format("%d", number);
    }

    private String formatMarketCap(Double marketCap) {
        if (marketCap == null || marketCap <= 0) return "N/A";
        
        System.out.println("DEBUG - Original marketCap value from API: " + marketCap);
        
        // Market cap is in millions from Finnhub API
        String formatted;
        if (marketCap >= 1000000.0) {
            formatted = String.format(Locale.US, "$%.2f trillion", marketCap / 1000000.0);
        } else if (marketCap >= 1000.0) {
            formatted = String.format(Locale.US, "$%.2f billion", marketCap / 1000.0);
        } else {
            formatted = String.format(Locale.US, "$%.2f million", marketCap);
        }
        
        System.out.println("DEBUG - Formatted marketCap: " + formatted);
        return formatted;
    }

    private String generateTechnicalIndicators(Stock stock) {
        double currentPrice = stock.getPrice();
        double high52Week = stock.getHigh52Week();
        double low52Week = stock.getLow52Week();
        
        // Calculate basic technical indicators
        double rsi = calculateSimpleRSI(stock);
        double distanceFrom52WeekHigh = ((high52Week - currentPrice) / high52Week) * 100;
        double distanceFrom52WeekLow = ((currentPrice - low52Week) / low52Week) * 100;
        
        StringBuilder indicators = new StringBuilder();
        
        // RSI interpretation
        indicators.append("RSI: ").append(String.format("%.2f", rsi)).append(" - ");
        if (rsi > 70) {
            indicators.append("overbought condition");
        } else if (rsi < 30) {
            indicators.append("oversold condition");
        } else {
            indicators.append("neutral");
        }
        
        // 52-week range position
        indicators.append("\n52-Week Range: ");
        indicators.append(String.format("%.2f", distanceFrom52WeekLow)).append("% from low, ");
        indicators.append(String.format("%.2f", distanceFrom52WeekHigh)).append("% from high");
        
        // Price to moving average relation (simple estimate)
        double estimatedMA50 = currentPrice * (1 - (stock.getChangePercent() / 200));
        indicators.append("\nPrice to MA50: ");
        if (currentPrice > estimatedMA50) {
            indicators.append("trading above (bullish)");
        } else {
            indicators.append("trading below (bearish)");
        }
        
        return indicators.toString();
    }
    
    // Simple RSI calculation without needing historical data
    private double calculateSimpleRSI(Stock stock) {
        // Use change percent as a proxy for RSI
        // This is a simplified version since we don't have real historical data
        double changePercent = stock.getChangePercent();
        
        // Map the change percent to a 0-100 scale centered around 50
        double rsi = 50 + (changePercent * 2);
        
        // Clamp between 0 and 100
        return Math.min(100, Math.max(0, rsi));
    }

    private static class CachedStock {
        final Stock stock;
        final long timestamp;
        
        CachedStock(Stock stock) {
            this.stock = stock;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TimeUnit.MINUTES.toMillis(CACHE_EXPIRY_MINUTES);
        }
    }
    
    private static class CachedStockDetails {
        final StockDetails details;
        final long timestamp;
        
        CachedStockDetails(StockDetails details) {
            this.details = details;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TimeUnit.HOURS.toMillis(DETAILS_CACHE_EXPIRY_HOURS);
        }
    }

    public Map<String, Object> getPeerComparison(String symbol) {
        Map<String, Object> result = new HashMap<>();
        try {
            // First get the stock details to ensure we have industry information
            StockDetails details = getStockDetails(symbol);
            if (details == null || details.getIndustry() == null || details.getIndustry().isEmpty()) {
                log.warn("No industry information available for stock: {}", symbol);
                return result;
            }

            // Then get the stock from database
            Optional<Stock> stockOpt = getStockBySymbol(symbol);
            if (!stockOpt.isPresent()) {
                return result;
            }
            
            Stock stock = stockOpt.get();
            stock.setIndustry(details.getIndustry()); // Ensure industry is set
            
            // Find other stocks in the same industry
            List<Stock> industryPeers = stockRepository.findByIndustry(details.getIndustry());
            if (industryPeers.size() <= 1) {
                log.info("No peers found for industry: {}", details.getIndustry());
                return result;
            }
            
            // Remove the current stock from the list
            industryPeers.removeIf(s -> s.getSymbol().equals(symbol));
            
            // Limit to top 5 peers by market cap
            industryPeers.sort((a, b) -> Double.compare(b.getMarketCap(), a.getMarketCap()));
            if (industryPeers.size() > 5) {
                industryPeers = industryPeers.subList(0, 5);
            }
            
            // Update peer data with latest information
            for (Stock peer : industryPeers) {
                try {
                    StockDetails peerDetails = getStockDetails(peer.getSymbol());
                    if (peerDetails != null) {
                        peer.setPrice(peerDetails.getPrice());
                        peer.setChangePercent(peerDetails.getChangePercent());
                        peer.setPeRatio(peerDetails.getPeRatio());
                        peer.setMarketCap(peerDetails.getMarketCap());
                    }
                } catch (Exception e) {
                    log.warn("Failed to update peer data for {}: {}", peer.getSymbol(), e.getMessage());
                }
            }
            
            // Basic metrics for comparison
            double avgPE = industryPeers.stream()
                    .map(Stock::getPeRatio)
                    .filter(pe -> pe != null && pe > 0.0)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            
            double avgChangePercent = industryPeers.stream()
                    .mapToDouble(Stock::getChangePercent)
                    .average()
                    .orElse(0.0);
            
            // Create comparison data
            List<Map<String, Object>> peers = new ArrayList<>();
            for (Stock peer : industryPeers) {
                Map<String, Object> peerData = new HashMap<>();
                peerData.put("symbol", peer.getSymbol());
                peerData.put("name", peer.getName());
                peerData.put("price", peer.getPrice());
                peerData.put("changePercent", peer.getChangePercent());
                Double peRatio = peer.getPeRatio();
                peerData.put("peRatio", (peRatio != null && peRatio > 0.0) ? peRatio : null);
                peerData.put("marketCap", formatMarketCap(peer.getMarketCap()));
                peers.add(peerData);
            }
            
            result.put("peers", peers);
            result.put("avgPE", avgPE > 0.0 ? avgPE : null);
            Double stockPE = stock.getPeRatio();
            result.put("currentPE", (stockPE != null && stockPE > 0.0) ? stockPE : null);
            result.put("avgChangePercent", avgChangePercent);
            result.put("relativeStrength", stock.getChangePercent() - avgChangePercent);
            
            String peerAnalysis = generatePeerAnalysis(stock, avgPE, avgChangePercent);
            result.put("analysis", peerAnalysis);
            
            return result;
        } catch (Exception e) {
            log.error("Error generating peer comparison for {}: {}", symbol, e.getMessage());
            return result;
        }
    }
    
    private String generatePeerAnalysis(Stock stock, double industryAvgPE, double industryAvgChange) {
        StringBuilder analysis = new StringBuilder();
        
        analysis.append(stock.getName())
                .append(" (")
                .append(stock.getSymbol())
                .append(") compared to industry peers: ");
        
        // PE ratio comparison
        if (stock.getPeRatio() > 0 && industryAvgPE > 0) {
            double peRatioDiff = ((stock.getPeRatio() / industryAvgPE) - 1) * 100;
            if (Math.abs(peRatioDiff) < 5) {
                analysis.append("P/E ratio is in line with the industry average");
            } else if (peRatioDiff > 0) {
                analysis.append("P/E ratio is ")
                        .append(String.format("%.1f", peRatioDiff))
                        .append("% higher than the industry average, suggesting a premium valuation");
            } else {
                analysis.append("P/E ratio is ")
                        .append(String.format("%.1f", Math.abs(peRatioDiff)))
                        .append("% lower than the industry average, suggesting a potential value opportunity");
            }
        }
        
        // Performance comparison
        double performanceDiff = stock.getChangePercent() - industryAvgChange;
        if (Math.abs(performanceDiff) < 1) {
            analysis.append(". Performance is in line with industry peers.");
        } else if (performanceDiff > 0) {
            analysis.append(". Outperforming industry peers by ")
                    .append(String.format("%.1f", performanceDiff))
                    .append(" percentage points.");
        } else {
            analysis.append(". Underperforming industry peers by ")
                    .append(String.format("%.1f", Math.abs(performanceDiff)))
                    .append(" percentage points.");
        }
        
        return analysis.toString();
    }

    public Stock saveStock(Stock stock) {
        try {
            log.debug("Saving stock: {}", stock.getSymbol());
            return stockRepository.save(stock);
        } catch (Exception e) {
            log.error("Error saving stock {}: {}", stock.getSymbol(), e.getMessage());
            throw new RuntimeException("Failed to save stock: " + e.getMessage());
        }
    }

    public void deleteStock(String symbol) {
        try {
            log.debug("Deleting stock: {}", symbol);
            stockRepository.deleteBySymbol(symbol);
        } catch (Exception e) {
            log.error("Error deleting stock {}: {}", symbol, e.getMessage());
            throw new RuntimeException("Failed to delete stock: " + e.getMessage());
        }
    }

    /**
     * Generates market-based news sentiment data when API calls fail
     * This provides more realistic and varied sentiment data instead of always returning neutral
     */
    private Map<String, Object> generateMarketBasedNewsSentiment(String symbol) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get stock data to base our sentiment on
            Optional<Stock> stockOpt = getStockBySymbol(symbol);
            if (!stockOpt.isPresent()) {
                log.warn("Stock not found for sentiment generation: {}", symbol);
                return createDefaultSentiment();
            }
            
            Stock stock = stockOpt.get();
            double changePercent = stock.getChangePercent();
            long volume = stock.getVolume();
            
            // Generate sentiment distribution based on stock performance
            int positiveCount = 0;
            int negativeCount = 0;
            int neutralCount = 0;
            String overallSentiment;
            double sentimentScore;
            
            // Create a distribution based on price movement
            if (changePercent >= 2.5) {
                positiveCount = 3 + (int)(Math.random() * 2); // 3-4 positive
                negativeCount = 1;
                neutralCount = 1 + (int)(Math.random() * 2); // 1-2 neutral
                overallSentiment = "bullish";
                sentimentScore = 0.3 + (Math.random() * 0.2); // 0.3-0.5
            } else if (changePercent >= 1.0) {
                positiveCount = 2 + (int)(Math.random() * 2); // 2-3 positive
                negativeCount = 1;
                neutralCount = 2;
                overallSentiment = "slightly bullish";
                sentimentScore = 0.15 + (Math.random() * 0.15); // 0.15-0.3
            } else if (changePercent <= -2.5) {
                positiveCount = 1;
                negativeCount = 3 + (int)(Math.random() * 2); // 3-4 negative
                neutralCount = 1 + (int)(Math.random() * 2); // 1-2 neutral
                overallSentiment = "bearish";
                sentimentScore = -0.3 - (Math.random() * 0.2); // -0.3 to -0.5
            } else if (changePercent <= -1.0) {
                positiveCount = 1;
                negativeCount = 2 + (int)(Math.random() * 2); // 2-3 negative
                neutralCount = 2;
                overallSentiment = "slightly bearish";
                sentimentScore = -0.15 - (Math.random() * 0.15); // -0.15 to -0.3
            } else {
                // Neutral price change, but still provide some variety
                int randomFactor = (int)(Math.random() * 3); // 0, 1, or 2
                if (changePercent > 0 || randomFactor == 1) {
                    // Slightly positive leaning
                    positiveCount = 2;
                    negativeCount = 1;
                    neutralCount = 2 + (int)(Math.random() * 2); // 2-3 neutral
                    overallSentiment = "neutral";
                    sentimentScore = 0.05 + (Math.random() * 0.1); // 0.05-0.15
                } else if (changePercent < 0 || randomFactor == 2) {
                    // Slightly negative leaning
                    positiveCount = 1;
                    negativeCount = 2;
                    neutralCount = 2 + (int)(Math.random() * 2); // 2-3 neutral
                    overallSentiment = "neutral";
                    sentimentScore = -0.05 - (Math.random() * 0.1); // -0.05 to -0.15
                } else {
                    // Perfectly balanced
                    positiveCount = 1 + (int)(Math.random() * 2); // 1-2 positive
                    negativeCount = 1 + (int)(Math.random() * 2); // 1-2 negative
                    neutralCount = 3;
                    overallSentiment = "neutral";
                    sentimentScore = -0.05 + (Math.random() * 0.1); // -0.05 to 0.05
                }
            }
            
            // Create sample articles with appropriate sentiment distribution
            List<Map<String, Object>> analyzedArticles = new ArrayList<>();
            
            // Generate positive articles
            for (int i = 0; i < positiveCount; i++) {
                Map<String, Object> article = new HashMap<>();
                article.put("title", getRandomPositiveHeadline(symbol, i));
                article.put("sentiment", "positive");
                analyzedArticles.add(article);
            }
            
            // Generate negative articles
            for (int i = 0; i < negativeCount; i++) {
                Map<String, Object> article = new HashMap<>();
                article.put("title", getRandomNegativeHeadline(symbol, i));
                article.put("sentiment", "negative");
                analyzedArticles.add(article);
            }
            
            // Generate neutral articles
            for (int i = 0; i < neutralCount; i++) {
                Map<String, Object> article = new HashMap<>();
                article.put("title", getRandomNeutralHeadline(symbol, i));
                article.put("sentiment", "neutral");
                analyzedArticles.add(article);
            }
            
            int totalArticles = positiveCount + negativeCount + neutralCount;
            
            // Build the sentiment result
            result.put("sentiment", overallSentiment);
            result.put("score", sentimentScore);
            result.put("positiveCount", positiveCount);
            result.put("negativeCount", negativeCount);
            result.put("neutralCount", neutralCount);
            result.put("totalArticles", totalArticles);
            result.put("analyzedArticles", analyzedArticles);
            result.put("generatedSentiment", true); // Flag that this is generated data
            
            log.info("Generated market-based sentiment for {}: {} (pos={}, neu={}, neg={})", 
                    symbol, overallSentiment, positiveCount, neutralCount, negativeCount);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error generating market-based sentiment: {}", e.getMessage());
            return createDefaultSentiment();
        }
    }
    
    /**
     * Creates a minimal default sentiment response as last resort
     */
    private Map<String, Object> createDefaultSentiment() {
        Map<String, Object> sentiment = new HashMap<>();
        sentiment.put("sentiment", "neutral");
        sentiment.put("score", 0);
        sentiment.put("positiveCount", 1);
        sentiment.put("negativeCount", 1);
        sentiment.put("neutralCount", 3);
        sentiment.put("totalArticles", 5);
        
        List<Map<String, Object>> analyzedArticles = new ArrayList<>();
        
        Map<String, Object> positiveArticle = new HashMap<>();
        positiveArticle.put("title", "Market update: Latest financial news");
        positiveArticle.put("sentiment", "positive");
        analyzedArticles.add(positiveArticle);
        
        Map<String, Object> negativeArticle = new HashMap<>();
        negativeArticle.put("title", "Investors weigh market conditions");
        negativeArticle.put("sentiment", "negative");
        analyzedArticles.add(negativeArticle);
        
        Map<String, Object> neutralArticle = new HashMap<>();
        neutralArticle.put("title", "Stock market analysis and trends");
        neutralArticle.put("sentiment", "neutral");
        analyzedArticles.add(neutralArticle);
        
        sentiment.put("analyzedArticles", analyzedArticles);
        
        return sentiment;
    }
    
    /**
     * Generate varied positive headlines
     */
    private String getRandomPositiveHeadline(String symbol, int index) {
        String[] templates = {
            "%s reports better-than-expected earnings",
            "Analysts upgrade %s following strong performance",
            "%s shares surge on positive outlook",
            "Investors bullish on %s growth prospects",
            "%s announces promising new developments",
            "Strong market position drives %s upward",
            "%s outperforms market expectations",
            "Positive outlook for %s in current market conditions",
            "%s stock climbs on sector strength",
            "%s shows momentum in latest trading session"
        };
        
        int templateIndex = (index + (int)(Math.random() * 3)) % templates.length;
        return String.format(templates[templateIndex], symbol);
    }
    
    /**
     * Generate varied negative headlines
     */
    private String getRandomNegativeHeadline(String symbol, int index) {
        String[] templates = {
            "%s shares drop on missed expectations",
            "Analysts downgrade %s amid market concerns",
            "Investors cautious about %s outlook",
            "%s faces headwinds in current market",
            "Challenges ahead for %s according to reports",
            "%s struggles to maintain momentum",
            "Market pressures impact %s performance",
            "%s falls short of analyst expectations",
            "Bearish sentiment grows around %s",
            "%s stock declines amid sector weakness"
        };
        
        int templateIndex = (index + (int)(Math.random() * 3)) % templates.length;
        return String.format(templates[templateIndex], symbol);
    }
    
    /**
     * Generate varied neutral headlines
     */
    private String getRandomNeutralHeadline(String symbol, int index) {
        String[] templates = {
            "%s trading activity shows mixed signals",
            "Market analysis: What's next for %s?",
            "%s maintains position despite market fluctuations",
            "Investors monitor %s amid changing conditions",
            "%s performance in line with expectations",
            "Mixed outlook for %s according to latest reports",
            "Analysts provide balanced view on %s prospects",
            "%s shows stability in volatile market",
            "What investors should know about %s",
            "%s reports quarterly results: Key takeaways"
        };
        
        int templateIndex = (index + (int)(Math.random() * 3)) % templates.length;
        return String.format(templates[templateIndex], symbol);
    }
}