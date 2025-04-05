package com.borsvy.service;

import com.borsvy.model.Favorite;
import com.borsvy.repository.FavoriteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class FavoriteService {

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
    public boolean removeFavorite(String symbol) {
        // Check if favorite exists
        if (!favoriteRepository.existsById(symbol)) {
            return false;
        }
        
        // Delete from database
        favoriteRepository.deleteById(symbol);
        return true;
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