package com.borsvy.service;

import com.borsvy.model.Favorite;
import com.borsvy.repository.FavoriteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    public List<Favorite> getFavoritesByUser(Long userId) {
        return favoriteRepository.findByUserId(userId);
    }

    @Transactional
    public Favorite addFavorite(Long userId, Favorite favorite) {
        if (favoriteRepository.existsByUserIdAndSymbol(userId, favorite.getSymbol())) {
            logger.info("Symbol {} already in favorites for user {}. Skipping.", favorite.getSymbol(), userId);
            return favoriteRepository.findByUserIdAndSymbol(userId, favorite.getSymbol()).orElse(favorite);
        }
        favorite.setUserId(userId);
        if (favorite.getAddedAt() == null) {
            favorite.setAddedAt(LocalDateTime.now());
        }
        return favoriteRepository.save(favorite);
    }

    @Transactional
    public boolean removeFavorite(Long userId, String symbol) {
        Optional<Favorite> fav = favoriteRepository.findByUserIdAndSymbol(userId, symbol);
        if (fav.isEmpty()) return false;
        favoriteRepository.delete(fav.get());
        return true;
    }

    public boolean isFavorite(Long userId, String symbol) {
        return favoriteRepository.existsByUserIdAndSymbol(userId, symbol);
    }
}
