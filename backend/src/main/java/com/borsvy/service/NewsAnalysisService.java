package com.borsvy.service;

import com.borsvy.model.NewsArticle;
import java.util.List;
import java.util.Map;

/**
 * Interface for news sentiment analysis to break circular dependencies
 */
public interface NewsAnalysisService {
    
    /**
     * Analyze sentiment of news articles for a stock
     * 
     * @param symbol The stock symbol
     * @param newsArticles The list of news articles to analyze
     * @return A map containing sentiment analysis results
     */
    Map<String, Object> analyzeNewsSentiment(String symbol, List<NewsArticle> newsArticles);
}
