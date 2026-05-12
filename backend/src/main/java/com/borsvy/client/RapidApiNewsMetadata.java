package com.borsvy.client;

import java.util.Map;

import static java.util.Map.entry;

final class RapidApiNewsMetadata {
    static final String[] POSITIVE_KEYWORDS = {
        "rally", "surge", "soar", "jump", "beat expectations", "exceed estimates",
        "upgrade", "bullish", "buy rating", "outperform", "strong growth",
        "record revenue", "record profit", "market leader", "increased dividend",
        "profit", "profitable", "promising", "momentum", "recovery", "breakthrough"
    };

    static final String[] NEGATIVE_KEYWORDS = {
        "decline", "drop", "fall", "slip", "slump", "tumble", "plunge", "crash",
        "bearish", "downgrade", "sell rating", "underperform", "miss expectations",
        "below estimates", "downside", "negative", "weak", "loss", "struggling",
        "concern", "risk", "uncertainty", "volatility", "warning", "crisis"
    };

    static final String[] STRONG_NEGATIVE_INDICATORS = {
        "stock down", "stocks down", "shares down", "shares fall", "stock falls", "stocks fall",
        "market crash", "stock crash", "shares crash", "sell-off", "selling off",
        "worst day", "worst week", "worst month", "big drop", "sharp decline",
        "heavy losses", "major losses", "tumbles", "plunges", "disaster",
        "disappointing earnings", "missed expectations", "below forecast",
        "layoffs", "job cuts", "bankruptcy", "class action", "fraud", "investigation"
    };

    static final String[] STRONG_POSITIVE_INDICATORS = {
        "stock up", "stocks up", "shares up", "shares rise", "stock rises", "stocks rise",
        "breakout", "record high", "all-time high", "new high", "multi-year high",
        "beats expectations", "exceeds forecast", "strong earnings", "strong quarter",
        "dividend increase", "raised guidance", "buy rating", "strong buy",
        "best day", "best week", "best month", "big gain", "sharp increase",
        "major gains", "soars", "surges", "rallies", "jumps"
    };

    static final String[] BROAD_POSITIVE_KEYWORDS = {
        "up", "rise", "gain", "positive", "strong", "growth", "profit", "success",
        "outperform", "beat", "exceed", "upgrade", "bullish", "rally", "surge", "soar",
        "jump", "boost", "upside", "opportunity", "recovery", "breakthrough", "momentum",
        "optimistic", "promising", "favorable", "advantage", "strength", "performance",
        "above consensus", "buy rating", "price target increase", "new high", "dividend increase",
        "beat earnings", "revenue growth", "market leader", "cost reduction", "synergies",
        "strategic acquisition", "expansion", "innovation", "improved guidance",
        "biggest bargain", "investing aggressively", "all-time high", "stock rise", "stocks rise",
        "AI", "artificial intelligence", "blockchain", "crypto", "metaverse", "cloud computing",
        "digital transformation", "e-commerce", "streaming", "subscription", "recurring revenue",
        "market share", "competitive advantage", "moat", "scalable", "disruptive", "innovative",
        "partnership", "collaboration", "integration", "acquisition", "merger", "deal",
        "expansion", "growth", "scale", "efficiency", "productivity", "automation"
    };

    static final String[] BROAD_NEGATIVE_KEYWORDS = {
        "down", "fall", "drop", "negative", "weak", "loss", "decline", "miss",
        "underperform", "downgrade", "bearish", "sink", "plunge", "tumble", "slip", "slump",
        "crash", "recession", "sell-off", "downside", "risk", "concern", "disappoint", "struggle",
        "pressure", "uncertainty", "volatility", "warning", "crisis", "challenge", "headwind",
        "below estimates", "sell rating", "price target cut", "new low", "dividend cut",
        "missed earnings", "revenue decline", "competitive pressure", "cost increase", "debt",
        "restructuring", "layoffs", "downtime", "investigation", "lawsuit", "recall",
        "regulatory issue", "delayed", "lowered guidance", "margin pressure",
        "crushing", "tariffs", "export controls", "falling", "trading lower", "stock down",
        "cybersecurity", "data breach", "privacy", "regulation", "compliance", "fine",
        "penalty", "investigation", "lawsuit", "class action", "settlement", "violation",
        "hack", "outage", "downtime", "disruption", "supply chain", "shortage", "inflation",
        "interest rates", "rate hike", "recession", "slowdown", "downturn", "correction",
        "bubble", "overvalued", "valuation", "expensive", "premium", "competition",
        "market share loss", "subscriber loss", "user decline", "engagement drop"
    };

    private static final String DEFAULT_FINANCIAL_THUMBNAIL =
        "https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=800&auto=format&fit=crop";

    private static final Map<String, String> SOURCE_THUMBNAILS = Map.ofEntries(
        entry("Yahoo Finance", "https://s.yimg.com/ny/api/res/1.2/2Qq8o3Ld_2PqL2K5L1XzGA--/YXBwaWQ9aGlnaGxhbmRlcjt3PTk2MDtoPTU0MDtjZj13ZWJw/https://s.yimg.com/uu/api/res/1.2/QxV2bGVfSVFnb2x3X1F3Z2t3L2h0dHBzOi8vd3d3LnlhaG9vLmNvbS9maW5hbmNlL2ltYWdlcy9kZWZhdWx0L2ZpbmFuY2lhbC1uZXdzLmpwZw--"),
        entry("Reuters", "https://www.reuters.com/pf/resources/images/reuters/reuters-default.png"),
        entry("Bloomberg", "https://assets.bwbx.io/s3/javelin/public/javelin/images/social-default-a4f15fa7ee.jpg"),
        entry("CNBC", "https://www.cnbc.com/pf/resources/images/CNBC_logo_reuters.png"),
        entry("MarketWatch", "https://s.marketwatch.com/public/resources/images/MW-HP535_market_ZH_20190123153019.jpg"),
        entry("Business Insider", "https://static.businessinsider.com/image/5d9d8b7c6f24eb1a0a2b3b5a-1200.jpg"),
        entry("Investor's Business Daily", "https://www.investors.com/wp-content/uploads/2019/01/IBD-logo.png"),
        entry("The Wall Street Journal", "https://s.wsj.net/img/WSJ_Logo_black_social.png"),
        entry("Barrons.com", "https://www.barrons.com/assets/img/barrons-logo.png"),
        entry("Motley Fool", "https://g.foolcdn.com/art/companylogos/square/tmf.png"),
        entry("Fortune", "https://fortune.com/favicon.ico"),
        entry("The Real Deal", "https://therealdeal.com/wp-content/uploads/2019/05/trd-logo.png"),
        entry("Insider Monkey", "https://www.insidermonkey.com/blog/wp-content/uploads/2019/01/insider-monkey-logo.png"),
        entry("Benzinga", "https://cdn.benzinga.com/files/images/story/2012/benzinga-logo.png"),
        entry("Investopedia", "https://www.investopedia.com/thmb/0YHt1qQvQw7Ckf6ENJh0QjXF8b4=/1500x0/filters:no_upscale():max_bytes(150000):strip_icc()/InvestopediaLogo-9c5b0a7f0b4b4798b0bfde9d5b0b8b3a.png"),
        entry("etf.com", "https://www.etf.com/sites/default/files/etf-com-logo.png"),
        entry("TheStreet", "https://www.thestreet.com/.image/t_share/MTc0NDU4NDg5ODQ5NDQ5NDQ5/thestreet-logo.png"),
        entry("CIO Dive", "https://www.ciodive.com/img/ciodive-logo.png")
    );

    private static final Map<String, String> COMPANY_NAMES = Map.ofEntries(
        entry("AAPL", "Apple"),
        entry("MSFT", "Microsoft"),
        entry("GOOGL", "Google"),
        entry("GOOG", "Google"),
        entry("AMZN", "Amazon"),
        entry("META", "Meta"),
        entry("NVDA", "Nvidia"),
        entry("TSLA", "Tesla"),
        entry("AMD", "Advanced Micro Devices"),
        entry("INTC", "Intel"),
        entry("CRM", "Salesforce"),
        entry("ADBE", "Adobe"),
        entry("NFLX", "Netflix"),
        entry("CSCO", "Cisco"),
        entry("JPM", "JPMorgan Chase"),
        entry("BAC", "Bank of America"),
        entry("WFC", "Wells Fargo"),
        entry("GS", "Goldman Sachs"),
        entry("MS", "Morgan Stanley"),
        entry("V", "Visa"),
        entry("MA", "Mastercard"),
        entry("AXP", "American Express"),
        entry("WMT", "Walmart"),
        entry("TGT", "Target"),
        entry("COST", "Costco"),
        entry("HD", "Home Depot"),
        entry("LOW", "Lowe's"),
        entry("NKE", "Nike"),
        entry("SBUX", "Starbucks"),
        entry("MCD", "McDonald's"),
        entry("JNJ", "Johnson & Johnson"),
        entry("PFE", "Pfizer"),
        entry("MRNA", "Moderna"),
        entry("UNH", "UnitedHealth"),
        entry("CVS", "CVS Health"),
        entry("T", "AT&T"),
        entry("VZ", "Verizon"),
        entry("CMCSA", "Comcast"),
        entry("DIS", "Disney"),
        entry("XOM", "ExxonMobil"),
        entry("CVX", "Chevron"),
        entry("BA", "Boeing"),
        entry("GE", "General Electric"),
        entry("F", "Ford"),
        entry("GM", "General Motors")
    );

    private RapidApiNewsMetadata() {
    }

    static String getDefaultThumbnailForSource(String source) {
        if (source == null) {
            return DEFAULT_FINANCIAL_THUMBNAIL;
        }
        return SOURCE_THUMBNAILS.getOrDefault(source, DEFAULT_FINANCIAL_THUMBNAIL);
    }

    static String getCompanyNameForSymbol(String symbol) {
        String companyName = COMPANY_NAMES.get(symbol);
        if (companyName != null) {
            return companyName;
        }

        if (symbol.length() > 1) {
            String cleanSymbol = symbol;
            String[] suffixes = {".US", "-US", ".L", ".TO", "-A", "-B", ".A", ".B", ".PR", ".PF"};
            for (String suffix : suffixes) {
                if (cleanSymbol.endsWith(suffix)) {
                    cleanSymbol = cleanSymbol.substring(0, cleanSymbol.length() - suffix.length());
                    break;
                }
            }
            return cleanSymbol;
        }

        return symbol;
    }
}
