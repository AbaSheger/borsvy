package com.borsvy.controller;

import com.borsvy.model.StockPrice;
import com.borsvy.model.StockDetails;
import com.borsvy.model.StockAnalysis;
import com.borsvy.model.Stock;
import com.borsvy.service.StockService;
import com.borsvy.service.AnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.stream.Collectors;
import com.borsvy.model.NewsArticle;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class StockController {

    private final StockService stockService;
    private final AnalysisService analysisService;
    private final Logger logger = LoggerFactory.getLogger(StockController.class);
    private final Map<String, StockDetails> stockDetailsCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Autowired
    public StockController(StockService stockService, AnalysisService analysisService) {
        this.stockService = stockService;
        this.analysisService = analysisService;
        // Schedule cache cleanup every hour
        scheduler.scheduleAtFixedRate(this::cleanupCache, 1, 1, TimeUnit.HOURS);
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<StockDetails> getStockDetails(@PathVariable String symbol) {
        try {
            // Check cache first
            StockDetails cachedDetails = stockDetailsCache.get(symbol);
            if (cachedDetails != null) {
                return ResponseEntity.ok(cachedDetails);
            }

            // If not in cache, fetch from service
            StockDetails details = stockService.getStockDetails(symbol);
            if (details != null) {
                // Cache the details
                stockDetailsCache.put(symbol, details);
                return ResponseEntity.ok(details);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error fetching stock details for {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<Stock>> searchStocks(@RequestParam String query) {
        try {
            List<Stock> results = stockService.searchStocks(query);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error searching stocks: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/popular")
    public ResponseEntity<List<Stock>> getPopularStocks() {
        try {
            List<Stock> popularStocks = stockService.getPopularStocks();
            return ResponseEntity.ok(popularStocks);
        } catch (Exception e) {
            logger.error("Error fetching popular stocks: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{symbol}/price-history")
    public ResponseEntity<List<StockPrice>> getPriceHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1d") String interval) {
        try {
            List<StockPrice> priceHistory = stockService.getHistoricalData(symbol, interval);
            return ResponseEntity.ok(priceHistory);
        } catch (Exception e) {
            logger.error("Error fetching price history for {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{symbol}/news")
    public ResponseEntity<List<Map<String, Object>>> getStockNews(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "5") int limit) {
        try {
            List<NewsArticle> newsArticles = stockService.getStockNews(symbol, limit);
            List<Map<String, Object>> news = newsArticles.stream()
                .map(article -> {
                    Map<String, Object> articleMap = new HashMap<>();
                    articleMap.put("title", article.getTitle());
                    articleMap.put("url", article.getUrl());
                    articleMap.put("date", article.getPublishedDate());
                    articleMap.put("thumbnail", article.getThumbnail());
                    articleMap.put("source", article.getSource());
                    articleMap.put("summary", article.getSummary());
                    return articleMap;
                })
                .collect(Collectors.toList());
            return ResponseEntity.ok(news);
        } catch (Exception e) {
            logger.error("Error fetching news for {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{symbol}/analysis")
    public ResponseEntity<Map<String, Object>> getStockAnalysis(@PathVariable String symbol) {
        try {
            Map<String, Object> analysis = analysisService.getCompleteAnalysis(symbol);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            logger.error("Error fetching analysis for {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{symbol}/peers")
    public ResponseEntity<Map<String, Object>> getPeerComparison(@PathVariable String symbol) {
        try {
            Map<String, Object> peerComparison = stockService.getPeerComparison(symbol);
            return ResponseEntity.ok(peerComparison);
        } catch (Exception e) {
            logger.error("Error fetching peer comparison for {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void cleanupCache() {
        stockDetailsCache.clear();
        logger.info("Stock details cache cleared");
    }
}