package com.borsvy.service;

import com.borsvy.model.Stock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class StockSentimentFallbackFactory {
    private static final String[] POSITIVE_HEADLINES = {
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

    private static final String[] NEGATIVE_HEADLINES = {
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

    private static final String[] NEUTRAL_HEADLINES = {
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

    private StockSentimentFallbackFactory() {
    }

    static Map<String, Object> create(Stock stock) {
        double changePercent = stock.getChangePercent();
        SentimentDistribution distribution = distributionFor(changePercent);
        List<Map<String, Object>> analyzedArticles = new ArrayList<>();

        addArticles(analyzedArticles, stock.getSymbol(), "positive", distribution.positiveCount, POSITIVE_HEADLINES);
        addArticles(analyzedArticles, stock.getSymbol(), "negative", distribution.negativeCount, NEGATIVE_HEADLINES);
        addArticles(analyzedArticles, stock.getSymbol(), "neutral", distribution.neutralCount, NEUTRAL_HEADLINES);

        int totalArticles = distribution.positiveCount + distribution.negativeCount + distribution.neutralCount;
        Map<String, Object> result = new HashMap<>();
        result.put("sentiment", distribution.overallSentiment);
        result.put("score", distribution.sentimentScore);
        result.put("positiveCount", distribution.positiveCount);
        result.put("negativeCount", distribution.negativeCount);
        result.put("neutralCount", distribution.neutralCount);
        result.put("totalArticles", totalArticles);
        result.put("analyzedArticles", analyzedArticles);
        result.put("generatedSentiment", true);
        return result;
    }

    static Map<String, Object> createDefault() {
        Map<String, Object> sentiment = new HashMap<>();
        sentiment.put("sentiment", "neutral");
        sentiment.put("score", 0);
        sentiment.put("positiveCount", 1);
        sentiment.put("negativeCount", 1);
        sentiment.put("neutralCount", 3);
        sentiment.put("totalArticles", 5);

        List<Map<String, Object>> analyzedArticles = new ArrayList<>();
        analyzedArticles.add(article("Market update: Latest financial news", "positive"));
        analyzedArticles.add(article("Investors weigh market conditions", "negative"));
        analyzedArticles.add(article("Stock market analysis and trends", "neutral"));
        sentiment.put("analyzedArticles", analyzedArticles);
        return sentiment;
    }

    private static SentimentDistribution distributionFor(double changePercent) {
        if (changePercent >= 2.5) {
            return new SentimentDistribution(3 + randomInt(2), 1, 1 + randomInt(2), "bullish", 0.3 + (Math.random() * 0.2));
        }
        if (changePercent >= 1.0) {
            return new SentimentDistribution(2 + randomInt(2), 1, 2, "slightly bullish", 0.15 + (Math.random() * 0.15));
        }
        if (changePercent <= -2.5) {
            return new SentimentDistribution(1, 3 + randomInt(2), 1 + randomInt(2), "bearish", -0.3 - (Math.random() * 0.2));
        }
        if (changePercent <= -1.0) {
            return new SentimentDistribution(1, 2 + randomInt(2), 2, "slightly bearish", -0.15 - (Math.random() * 0.15));
        }

        int randomFactor = randomInt(3);
        if (changePercent > 0 || randomFactor == 1) {
            return new SentimentDistribution(2, 1, 2 + randomInt(2), "neutral", 0.05 + (Math.random() * 0.1));
        }
        if (changePercent < 0 || randomFactor == 2) {
            return new SentimentDistribution(1, 2, 2 + randomInt(2), "neutral", -0.05 - (Math.random() * 0.1));
        }
        return new SentimentDistribution(1 + randomInt(2), 1 + randomInt(2), 3, "neutral", -0.05 + (Math.random() * 0.1));
    }

    private static void addArticles(List<Map<String, Object>> articles, String symbol, String sentiment, int count, String[] templates) {
        for (int i = 0; i < count; i++) {
            int templateIndex = (i + randomInt(3)) % templates.length;
            articles.add(article(String.format(templates[templateIndex], symbol), sentiment));
        }
    }

    private static Map<String, Object> article(String title, String sentiment) {
        Map<String, Object> article = new HashMap<>();
        article.put("title", title);
        article.put("sentiment", sentiment);
        return article;
    }

    private static int randomInt(int bound) {
        return (int) (Math.random() * bound);
    }

    private static final class SentimentDistribution {
        private final int positiveCount;
        private final int negativeCount;
        private final int neutralCount;
        private final String overallSentiment;
        private final double sentimentScore;

        private SentimentDistribution(int positiveCount, int negativeCount, int neutralCount, String overallSentiment, double sentimentScore) {
            this.positiveCount = positiveCount;
            this.negativeCount = negativeCount;
            this.neutralCount = neutralCount;
            this.overallSentiment = overallSentiment;
            this.sentimentScore = sentimentScore;
        }
    }
}
