package com.borsvy.service;

import com.borsvy.client.FinnhubClient;
import com.borsvy.model.CompanyProfile2;
import com.borsvy.model.Quote;
import com.borsvy.model.Stock;
import com.borsvy.model.StockDetails;
import com.borsvy.model.StockPrice;
import com.borsvy.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StockServiceTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private FinnhubClient finnhubClient;

    @InjectMocks
    private StockService stockService;

    private Stock testStock;
    private Quote testQuote;
    private CompanyProfile2 testProfile;

    @BeforeEach
    void setUp() {
        // Create test stock
        testStock = new Stock();
        testStock.setSymbol("AAPL");
        testStock.setName("Apple Inc.");
        testStock.setPrice(150.0);
        testStock.setChange(2.0);
        testStock.setChangePercent(1.33);
        testStock.setHigh(155.0);
        testStock.setLow(145.0);
        testStock.setOpen(148.0);
        testStock.setVolume(1000000L);
        testStock.setMarketCap(2500.0);
        testStock.setPeRatio(25.0);
        testStock.setBeta(1.2);
        testStock.setLastUpdated(LocalDateTime.now());

        // Create test quote
        testQuote = new Quote();
        testQuote.setCurrentPrice(150.0);
        testQuote.setChange(2.0);
        testQuote.setPercentChange(1.33);
        testQuote.setHigh(155.0);
        testQuote.setLow(145.0);
        testQuote.setOpen(148.0);
        testQuote.setVolume(1000000L);

        // Create test profile
        testProfile = new CompanyProfile2();
        testProfile.setName("Apple Inc.");
        testProfile.setFinnhubIndustry("Technology");
        testProfile.setMarketCapitalization(2500.0);
        testProfile.setPe(25.0);
        testProfile.setBeta(1.2);
    }

    @Test
    void testGetPopularStocks() {
        when(stockRepository.findAllById(any())).thenReturn(Arrays.asList(testStock));
        
        List<Stock> stocks = stockService.getPopularStocks();
        assertFalse(stocks.isEmpty());
        assertEquals("AAPL", stocks.get(0).getSymbol());
        verify(stockRepository).findAllById(any());
    }

    @Test
    void testGetStockBySymbol() {
        when(stockRepository.findById("AAPL")).thenReturn(Optional.of(testStock));
        
        Optional<Stock> result = stockService.getStockBySymbol("AAPL");
        
        assertTrue(result.isPresent());
        assertEquals("AAPL", result.get().getSymbol());
        assertEquals("Apple Inc.", result.get().getName());
        assertEquals(150.0, result.get().getPrice());
    }

    @Test
    void testGetStockDetails() throws Exception {
        when(finnhubClient.getQuote("AAPL")).thenReturn(testQuote);
        when(finnhubClient.getCompanyProfile2("AAPL")).thenReturn(testProfile);

        StockDetails details = stockService.getStockDetails("AAPL");

        assertNotNull(details);
        assertEquals("AAPL", details.getSymbol());
        assertEquals("Apple Inc.", details.getName());
        assertEquals("Technology", details.getIndustry());
        assertEquals(150.0, details.getPrice());
        assertEquals(1.33, details.getChange());
        assertEquals(155.0, details.getHigh());
        assertEquals(145.0, details.getLow());
        assertEquals(148.0, details.getOpen());
        assertEquals(1000000L, details.getVolume());
        assertEquals(25.0, details.getPeRatio());
        assertEquals(1.2, details.getBeta());
        assertEquals(2500.0, details.getMarketCap());
    }

    @Test
    void testGetPriceHistory() {
        when(stockRepository.findById("AAPL")).thenReturn(Optional.of(testStock));
        
        List<StockPrice> priceHistory = stockService.getHistoricalData("AAPL", "1d");
        assertFalse(priceHistory.isEmpty());
        assertEquals("AAPL", priceHistory.get(0).getSymbol());
        verify(stockRepository).findById("AAPL");
    }

    @Test
    void testSearchStocks() {
        when(stockRepository.findBySymbolContainingIgnoreCaseOrNameContainingIgnoreCase("AAPL", "AAPL"))
            .thenReturn(List.of(testStock));

        List<Stock> results = stockService.searchStocks("AAPL");

        assertFalse(results.isEmpty());
        assertEquals(1, results.size());
        assertEquals("AAPL", results.get(0).getSymbol());
    }
}