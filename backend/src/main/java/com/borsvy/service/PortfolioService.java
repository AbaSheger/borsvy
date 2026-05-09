package com.borsvy.service;

import com.borsvy.model.PortfolioHolding;
import com.borsvy.repository.PortfolioHoldingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class PortfolioService {

    private final PortfolioHoldingRepository repository;

    public PortfolioService(PortfolioHoldingRepository repository) {
        this.repository = repository;
    }

    public List<PortfolioHolding> getHoldings(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public PortfolioHolding addHolding(Long userId, PortfolioHolding holding) {
        validateHolding(holding);
        holding.setId(null);
        holding.setUserId(userId);
        holding.setSymbol(holding.getSymbol().trim().toUpperCase(Locale.ROOT));
        return repository.save(holding);
    }

    @Transactional
    public boolean removeHolding(Long userId, Long id) {
        Optional<PortfolioHolding> existing = repository.findByIdAndUserId(id, userId);
        existing.ifPresent(holding -> repository.deleteByIdAndUserId(id, userId));
        return existing.isPresent();
    }

    private void validateHolding(PortfolioHolding holding) {
        if (holding.getSymbol() == null || holding.getSymbol().trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        if (holding.getShares() == null || holding.getShares() <= 0) {
            throw new IllegalArgumentException("Shares must be positive");
        }
        if (holding.getBuyPrice() == null || holding.getBuyPrice() <= 0) {
            throw new IllegalArgumentException("Buy price must be positive");
        }
    }
}
