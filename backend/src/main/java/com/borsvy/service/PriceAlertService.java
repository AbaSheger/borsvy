package com.borsvy.service;

import com.borsvy.model.PriceAlert;
import com.borsvy.repository.PriceAlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class PriceAlertService {

    private final PriceAlertRepository repository;

    public PriceAlertService(PriceAlertRepository repository) {
        this.repository = repository;
    }

    public List<PriceAlert> getAlerts(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public PriceAlert addAlert(Long userId, PriceAlert alert) {
        validateAlert(alert);
        alert.setId(null);
        alert.setUserId(userId);
        alert.setSymbol(alert.getSymbol().trim().toUpperCase(Locale.ROOT));
        alert.setDirection(alert.getDirection().trim().toLowerCase(Locale.ROOT));
        alert.setActive(true);
        alert.setTriggered(false);
        alert.setTriggeredAt(null);
        return repository.save(alert);
    }

    public Optional<PriceAlert> toggleAlert(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId).map(alert -> {
            alert.setActive(!alert.isActive());
            return repository.save(alert);
        });
    }

    public Optional<PriceAlert> markTriggered(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId).map(alert -> {
            alert.setTriggered(true);
            alert.setActive(false);
            alert.setTriggeredAt(LocalDateTime.now());
            return repository.save(alert);
        });
    }

    @Transactional
    public boolean removeAlert(Long userId, Long id) {
        Optional<PriceAlert> existing = repository.findByIdAndUserId(id, userId);
        existing.ifPresent(alert -> repository.deleteByIdAndUserId(id, userId));
        return existing.isPresent();
    }

    private void validateAlert(PriceAlert alert) {
        if (alert.getSymbol() == null || alert.getSymbol().trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        if (alert.getTargetPrice() == null || alert.getTargetPrice() <= 0) {
            throw new IllegalArgumentException("Target price must be positive");
        }
        if (alert.getDirection() == null ||
            (!alert.getDirection().equalsIgnoreCase("above") && !alert.getDirection().equalsIgnoreCase("below"))) {
            throw new IllegalArgumentException("Direction must be above or below");
        }
    }
}
