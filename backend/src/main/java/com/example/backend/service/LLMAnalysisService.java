package com.example.backend.service;

import org.springframework.stereotype.Service;

@Service
public class LLMAnalysisService {
    
    public SentimentResult analyzeSentiment(String analysis) {
        // Count sentiment indicators
        int positiveIndicators = 0;
        int negativeIndicators = 0;
        
        // More comprehensive sentiment indicators
        String[] positiveWords = {"positive", "good", "strong", "growth", "increase", "profit", "success", "improve", "bullish", "upward"};
        String[] negativeWords = {"negative", "bad", "weak", "decline", "decrease", "loss", "failure", "worsen", "bearish", "downward", "risk", "concern", "worry", "trouble", "problem"};
        
        for (String word : positiveWords) {
            if (analysis.toLowerCase().contains(word)) positiveIndicators++;
        }
        for (String word : negativeWords) {
            if (analysis.toLowerCase().contains(word)) negativeIndicators++;
        }
        
        // Enhanced sentiment determination with higher sensitivity to negative indicators
        String sentiment;
        double confidence;
        
        if (positiveIndicators > negativeIndicators) {
            sentiment = "POSITIVE";
            confidence = 0.6 + (positiveIndicators - negativeIndicators) * 0.1;
        } else if (negativeIndicators > positiveIndicators) {
            sentiment = "NEGATIVE";
            confidence = 0.6 + (negativeIndicators - positiveIndicators) * 0.1;
        } else {
            sentiment = "NEUTRAL";
            confidence = 0.5;
        }
        
        // Cap confidence at 0.95
        confidence = Math.min(confidence, 0.95);
        
        return new SentimentResult(sentiment, confidence);
    }
    
    public static class SentimentResult {
        private final String sentiment;
        private final double confidence;
        
        public SentimentResult(String sentiment, double confidence) {
            this.sentiment = sentiment;
            this.confidence = confidence;
        }
        
        public String getSentiment() {
            return sentiment;
        }
        
        public double getConfidence() {
            return confidence;
        }
    }
} 