package com.borsvy.controller;

import com.borsvy.model.NewsArticle;
import com.borsvy.service.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/news")
@CrossOrigin(origins = "*")
public class NewsController {

    @Autowired
    private StockService stockService;

    @GetMapping("/sentiment/{symbol}")
    public Map<String, Object> getNewsSentiment(@PathVariable String symbol) {
        return stockService.getNewsSentiment(symbol);
    }

    @GetMapping("/stock/{symbol}")
    public List<NewsArticle> getStockNews(@PathVariable String symbol) {
        return stockService.getStockNews(symbol, 10);
    }
} 