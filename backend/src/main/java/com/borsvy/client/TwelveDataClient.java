package com.borsvy.client;

import com.borsvy.model.StockPrice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TwelveDataClient {

    private static final Logger logger = LoggerFactory.getLogger(TwelveDataClient.class);

    private static final DateTimeFormatter INTRADAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DAILY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final String apiKey;
    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Cache: key = "symbol_interval", value = cached entry
    private final Map<String, CachedHistory> cache = new ConcurrentHashMap<>();

    // Known crypto symbols — Twelve Data requires "BTC/USD" format for these
    private static final Set<String> CRYPTO_SYMBOLS = Set.of(
        "BTC", "ETH", "BNB", "SOL", "XRP", "ADA", "DOGE", "DOT", "AVAX",
        "MATIC", "LINK", "LTC", "BCH", "XLM", "ATOM", "UNI", "ALGO"
    );

    @Autowired
    public TwelveDataClient(@Value("${twelvedata.api.key}") String apiKey,
                            @Value("${twelvedata.api.url}") String baseUrl,
                            RestTemplate restTemplate,
                            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean isCrypto(String symbol) {
        return symbol != null && CRYPTO_SYMBOLS.contains(symbol.toUpperCase());
    }

    private String resolveSymbol(String symbol) {
        if (isCrypto(symbol)) {
            return symbol.toUpperCase() + "/USD";
        }
        return symbol;
    }

    /** Returns current quote data for a crypto symbol, or null on failure. */
    public Map<String, Object> getCryptoQuote(String symbol) {
        try {
            String resolvedSymbol = resolveSymbol(symbol);
            String url = String.format("%s/quote?symbol=%s&apikey=%s", baseUrl, resolvedSymbol, apiKey);
            logger.debug("Calling Twelve Data quote: {}", url.replace(apiKey, "API_KEY_REDACTED"));

            String response = restTemplate.getForObject(url, String.class);
            if (response == null) return null;

            JsonNode root = objectMapper.readTree(response);
            if (root.has("status") && "error".equals(root.path("status").asText())) {
                logger.warn("Twelve Data quote error for {}: {}", symbol, root.path("message").asText());
                return null;
            }

            Map<String, Object> quote = new java.util.HashMap<>();
            quote.put("symbol", symbol.toUpperCase());
            quote.put("name", root.path("name").asText(symbol));
            quote.put("price", root.path("close").asDouble(0));
            quote.put("change", root.path("change").asDouble(0));
            quote.put("changePercent", root.path("percent_change").asDouble(0));
            quote.put("high", root.path("high").asDouble(0));
            quote.put("low", root.path("low").asDouble(0));
            quote.put("volume", root.path("volume").asLong(0));
            quote.put("exchange", root.path("exchange").asText("Crypto"));
            return quote;

        } catch (Exception e) {
            logger.warn("Error fetching crypto quote for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    public List<StockPrice> getHistoricalData(String symbol, String interval) {
        String cacheKey = symbol + "_" + interval;
        CachedHistory cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired(cacheTtlMinutes(interval))) {
            logger.debug("Returning cached history for {} interval={}", symbol, interval);
            return cached.data;
        }

        try {
            String tdInterval = toTwelveDataInterval(interval);
            int outputSize = toOutputSize(interval);
            String resolvedSymbol = resolveSymbol(symbol);

            String url = String.format("%s/time_series?symbol=%s&interval=%s&outputsize=%d&apikey=%s",
                    baseUrl, resolvedSymbol, tdInterval, outputSize, apiKey);

            logger.debug("Calling Twelve Data: {}", url.replace(apiKey, "API_KEY_REDACTED"));

            String response = restTemplate.getForObject(url, String.class);
            if (response == null) {
                logger.warn("Twelve Data returned null response for {}", symbol);
                return new ArrayList<>();
            }

            JsonNode root = objectMapper.readTree(response);

            if (root.has("status") && "error".equals(root.path("status").asText())) {
                logger.warn("Twelve Data API error for {}: {}", symbol, root.path("message").asText());
                return new ArrayList<>();
            }

            JsonNode values = root.path("values");
            if (!values.isArray() || values.isEmpty()) {
                logger.warn("Twelve Data returned no values for {} interval={}", symbol, interval);
                return new ArrayList<>();
            }

            boolean isIntraday = tdInterval.contains("min") || tdInterval.contains("h");

            List<StockPrice> prices = new ArrayList<>();
            // Twelve Data returns newest-first; reverse to get chronological order
            for (int i = values.size() - 1; i >= 0; i--) {
                JsonNode entry = values.get(i);
                StockPrice sp = new StockPrice();
                sp.setSymbol(symbol);
                sp.setPrice(entry.path("close").asDouble());
                sp.setOpen(entry.path("open").asDouble(0));
                sp.setHigh(entry.path("high").asDouble(0));
                sp.setLow(entry.path("low").asDouble(0));
                sp.setVolume(entry.path("volume").asLong(0));
                String datetime = entry.path("datetime").asText();
                if (isIntraday) {
                    sp.setTimestamp(LocalDateTime.parse(datetime, INTRADAY_FMT));
                } else {
                    sp.setTimestamp(LocalDate.parse(datetime, DAILY_FMT).atStartOfDay());
                }
                prices.add(sp);
            }

            cache.put(cacheKey, new CachedHistory(prices));
            return prices;

        } catch (Exception e) {
            logger.warn("Error fetching historical data from Twelve Data for {}: {}", symbol, e.getMessage());
            return new ArrayList<>();
        }
    }

    private String toTwelveDataInterval(String interval) {
        if (interval == null) return "1day";
        switch (interval.toLowerCase()) {
            case "1d": return "5min";
            case "1w": return "1h";
            case "1m": return "1day";
            case "3m": return "1day";
            case "6m": return "1day";
            case "1y": return "1week";
            case "5y": return "1month";
            default:   return "1day";
        }
    }

    private int toOutputSize(String interval) {
        if (interval == null) return 78;
        switch (interval.toLowerCase()) {
            case "1d": return 78;
            case "1w": return 40;
            case "1m": return 22;
            case "3m": return 66;
            case "6m": return 130;
            case "1y": return 52;
            case "5y": return 60;
            default:   return 78;
        }
    }

    private long cacheTtlMinutes(String interval) {
        if (interval == null) return 5;
        switch (interval.toLowerCase()) {
            case "1d": return 5;
            case "1w":
            case "1m": return 30;
            default:   return 360; // 6 hours for 3m/6m/1y/5y — historical data doesn't change
        }
    }

    private static class CachedHistory {
        final List<StockPrice> data;
        final long fetchedAt;

        CachedHistory(List<StockPrice> data) {
            this.data = data;
            this.fetchedAt = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMinutes) {
            return System.currentTimeMillis() - fetchedAt > ttlMinutes * 60 * 1000;
        }
    }
}
