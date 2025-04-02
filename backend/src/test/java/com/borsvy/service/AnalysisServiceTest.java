package com.borsvy.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class AnalysisServiceTest {

    @Autowired
    private AnalysisService analysisService;

    @Test
    public void testFormatMarketCap() throws Exception {
        // Use reflection to access the private method
        Method formatMarketCapMethod = AnalysisService.class.getDeclaredMethod("formatMarketCap", Double.class);
        formatMarketCapMethod.setAccessible(true);
        
        // Test cases
        // Test a regular billion value (e.g., 45.67 billion)
        assertEquals("$45.67 billion", formatMarketCapMethod.invoke(analysisService, new Object[]{45.67}));
        
        // Test a trillion value (e.g., 2.45 trillion, which is 2450 billion from Finnhub)
        assertEquals("$2.45 trillion", formatMarketCapMethod.invoke(analysisService, new Object[]{2450.0}));
        
        // Test a large trillion value (e.g., Microsoft might be around 3 trillion)
        assertEquals("$3.00 trillion", formatMarketCapMethod.invoke(analysisService, new Object[]{3000.0}));
        
        // Test an edge case
        assertEquals("$1.00 trillion", formatMarketCapMethod.invoke(analysisService, new Object[]{1000.0}));
        
        // Test null or negative values
        assertEquals("N/A", formatMarketCapMethod.invoke(analysisService, new Object[]{null}));
        assertEquals("N/A", formatMarketCapMethod.invoke(analysisService, new Object[]{-1.0}));
    }
}