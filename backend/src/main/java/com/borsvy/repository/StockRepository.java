package com.borsvy.repository;

import com.borsvy.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, String> {
    List<Stock> findBySymbolContainingIgnoreCaseOrNameContainingIgnoreCase(String symbol, String name);
    
    List<Stock> findByIndustry(String industry);

    Optional<Stock> findBySymbol(String symbol);
    void deleteBySymbol(String symbol);
}