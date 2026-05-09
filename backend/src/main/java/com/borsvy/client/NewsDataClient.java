package com.borsvy.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@Component
public class NewsDataClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${newsdata.api.key}")
    private String apiKey;

    @Value("${newsdata.api.url:https://newsdata.io/api/1}")
    private String apiUrl;

    @Autowired
    public NewsDataClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> getStockNews(String symbol, int limit) {
        List<Map<String, Object>> articles = new ArrayList<>();
        try {
            String query = symbol + " stock";
            String url = apiUrl + "/news?apikey=" + apiKey
                    + "&q=" + query
                    + "&language=en"
                    + "&category=business";

            log.debug("Calling NewsData.io: {}", url.replace(apiKey, "API_KEY_REDACTED"));

            String response = restTemplate.getForObject(url, String.class);
            if (response == null) {
                log.warn("NewsData.io returned null response for {}", symbol);
                return articles;
            }

            JsonNode root = objectMapper.readTree(response);

            if (!"success".equals(root.path("status").asText())) {
                log.warn("NewsData.io error for {}: {}", symbol, root.path("message").asText());
                return articles;
            }

            JsonNode results = root.path("results");
            if (!results.isArray()) return articles;

            for (JsonNode item : results) {
                String title = item.path("title").asText("");
                String link = item.path("link").asText("");
                if (title.isEmpty() || link.isEmpty()) continue;

                Map<String, Object> article = new HashMap<>();
                article.put("title", title);
                article.put("url", link);
                article.put("source", item.path("source_id").asText("NewsData"));
                article.put("date", item.path("pubDate").asText(""));
                article.put("summary", item.path("description").asText(""));

                JsonNode imageUrl = item.path("image_url");
                if (!imageUrl.isNull() && !imageUrl.isMissingNode()) {
                    article.put("thumbnail", imageUrl.asText());
                }

                articles.add(article);
                if (articles.size() >= limit) break;
            }

            log.info("Returning {} news articles from NewsData.io for {}", articles.size(), symbol);

        } catch (Exception e) {
            log.error("Error fetching news from NewsData.io for {}: {}", symbol, e.getMessage());
        }
        return articles;
    }

    public Map<String, Object> getNewsSentiment(String symbol) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> articles = getStockNews(symbol, 10);

            int positiveCount = 0;
            int negativeCount = 0;
            int neutralCount = 0;

            for (Map<String, Object> article : articles) {
                String text = (article.getOrDefault("title", "") + " " + article.getOrDefault("summary", "")).toLowerCase();
                if (containsPositiveKeywords(text)) {
                    positiveCount++;
                } else if (containsNegativeKeywords(text)) {
                    negativeCount++;
                } else {
                    neutralCount++;
                }
            }

            String sentiment;
            if (positiveCount > negativeCount + neutralCount) {
                sentiment = "bullish";
            } else if (negativeCount > positiveCount + neutralCount) {
                sentiment = "bearish";
            } else if (positiveCount > negativeCount) {
                sentiment = "slightly bullish";
            } else if (negativeCount > positiveCount) {
                sentiment = "slightly bearish";
            } else {
                sentiment = "neutral";
            }

            result.put("sentiment", sentiment);
            result.put("positiveCount", positiveCount);
            result.put("negativeCount", negativeCount);
            result.put("neutralCount", neutralCount);

        } catch (Exception e) {
            log.error("Error calculating news sentiment for {}: {}", symbol, e.getMessage());
            result.put("error", e.getMessage());
            result.put("sentiment", "neutral");
        }
        return result;
    }

    private boolean containsPositiveKeywords(String text) {
        String[] positiveKeywords = {
            "buy", "bullish", "upgrade", "growth", "profit", "gain", "positive", "beat", "exceed",
            "outperform", "up", "higher", "rising", "surge", "rally", "strong", "boom", "success",
            "opportunity", "recommend", "upside", "optimistic", "promising", "innovation"
        };
        for (String keyword : positiveKeywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private boolean containsNegativeKeywords(String text) {
        String[] negativeKeywords = {
            "sell", "bearish", "downgrade", "decline", "loss", "negative", "miss", "below",
            "underperform", "down", "lower", "falling", "drop", "crash", "weak", "bust", "failure",
            "risk", "avoid", "downside", "pessimistic", "concerning", "disappointing", "investigation"
        };
        for (String keyword : negativeKeywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }
}
