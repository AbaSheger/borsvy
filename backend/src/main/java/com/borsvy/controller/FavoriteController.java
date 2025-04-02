package com.borsvy.controller;

import com.borsvy.model.Favorite;
import com.borsvy.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;
    
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Favorite>> getAllFavorites() {
        try {
            List<Favorite> favorites = favoriteService.getAllFavorites();
            return ResponseEntity.ok(favorites);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Favorite> addFavorite(@RequestBody Favorite favorite) {
        try {
            Favorite savedFavorite = favoriteService.addFavorite(favorite);
            return ResponseEntity.ok(savedFavorite);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> removeFavorite(@PathVariable String symbol) {
        try {
            boolean removed = favoriteService.removeFavorite(symbol);
            
            if (!removed) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/check/{symbol}")
    public ResponseEntity<Boolean> checkFavorite(@PathVariable String symbol) {
        try {
            boolean isFavorite = favoriteService.isFavorite(symbol);
            return ResponseEntity.ok(isFavorite);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}