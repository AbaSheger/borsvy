package com.borsvy.repository;

import com.borsvy.model.StockAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StockAnalysisRepository extends JpaRepository<StockAnalysis, String> {
    List<StockAnalysis> findBySymbolOrderByTimestampDesc(String symbol);
}