package com.borsvy.controller;

import com.borsvy.service.LLMAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/llm-analysis")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class LLMAnalysisController {

    private final LLMAnalysisService llmAnalysisService;

    @Autowired
    public LLMAnalysisController(LLMAnalysisService llmAnalysisService) {
        this.llmAnalysisService = llmAnalysisService;
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<Map<String, Object>> getLLMAnalysis(@PathVariable String symbol) {
        Map<String, Object> analysis = llmAnalysisService.generateAnalysis(symbol);
        return ResponseEntity.ok(analysis);
    }

    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testLLMIntegration() {
        // Test with a well-known stock
        Map<String, Object> analysis = llmAnalysisService.generateAnalysis("AAPL");
        return ResponseEntity.ok(analysis);
    }
} 