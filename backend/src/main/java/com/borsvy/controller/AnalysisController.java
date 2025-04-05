package com.borsvy.controller;

import com.borsvy.service.AnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/analysis")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class AnalysisController {

    private final AnalysisService analysisService;

    @Autowired
    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<Map<String, Object>> getAnalysis(@PathVariable String symbol) {
        Map<String, Object> analysis = analysisService.getCompleteAnalysis(symbol);
        return ResponseEntity.ok(analysis);
    }

    @GetMapping("/{symbol}/price-history")
    public ResponseEntity<List<Map<String, Object>>> getPriceHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1D") String interval) {
        List<Map<String, Object>> priceHistory = analysisService.getPriceHistory(symbol, interval);
        return ResponseEntity.ok(priceHistory);
    }
}