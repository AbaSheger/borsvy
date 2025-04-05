package com.borsvy.service;

import com.borsvy.model.Favorite;
import com.borsvy.repository.FavoriteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class FavoriteService {

    private static final Logger logger = LoggerFactory.getLogger(FavoriteService.class);

    @Autowired
    private FavoriteRepository favoriteRepository;
    
    /**
     * Get all favorite stocks
     */
    public List<Favorite> getAllFavorites() {
        return favoriteRepository.findAll();
    }
    
    /**
     * Add a stock to favorites
     */
    @Transactional
    public Favorite addFavorite(Favorite favorite) {
        // Set the current time if not provided
        if (favorite.getAddedAt() == null) {
            favorite.setAddedAt(LocalDateTime.now());
        }
        
        // Save to database
        return favoriteRepository.save(favorite);
    }
    
    /**
     * Remove a stock from favorites
     */
    @Transactional
    public boolean removeFavorite(String symbol) {
        try {
            // Get the favorite first to ensure we have the latest version
            Optional<Favorite> favorite = favoriteRepository.findById(symbol);
            if (favorite.isEmpty()) {
                return false;
            }
            
            // Delete from database
            favoriteRepository.delete(favorite.get());
            return true;
        } catch (ObjectOptimisticLockingFailureException e) {
            logger.warn("Concurrent modification detected while removing favorite: {}. Retrying...", symbol);
            // If we get a concurrent modification, try one more time
            try {
                Optional<Favorite> favorite = favoriteRepository.findById(symbol);
                if (favorite.isEmpty()) {
                    return false;
                }
                favoriteRepository.delete(favorite.get());
                return true;
            } catch (Exception retryEx) {
                logger.error("Failed to remove favorite {} after retry: {}", symbol, retryEx.getMessage());
                return false;
            }
        }
    }
    
    /**
     * Check if a stock is in favorites
     */
    public boolean isFavorite(String symbol) {
        return favoriteRepository.existsById(symbol);
    }
    
    /**
     * Get a specific favorite by symbol
     */
    public Optional<Favorite> getFavoriteBySymbol(String symbol) {
        return favoriteRepository.findById(symbol);
    }
}