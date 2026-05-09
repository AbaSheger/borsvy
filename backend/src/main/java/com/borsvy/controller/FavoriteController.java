package com.borsvy.controller;

import com.borsvy.model.Favorite;
import com.borsvy.security.UserPrincipal;
import com.borsvy.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    // Guest user ID — used when no account is logged in (shared, server-side)
    // Phase 2 will enforce per-user limits; for now unauthenticated users share a guest slot
    private static final Long GUEST_USER_ID = 0L;

    @Autowired
    private FavoriteService favoriteService;

    private Long resolveUserId(UserPrincipal principal) {
        return principal != null ? principal.getId() : GUEST_USER_ID;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Favorite>> getAllFavorites(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            return ResponseEntity.ok(favoriteService.getFavoritesByUser(resolveUserId(principal)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Favorite> addFavorite(@RequestBody Favorite favorite,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        try {
            Favorite saved = favoriteService.addFavorite(resolveUserId(principal), favorite);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> removeFavorite(@PathVariable String symbol,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        try {
            boolean removed = favoriteService.removeFavorite(resolveUserId(principal), symbol);
            return removed ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/check/{symbol}")
    public ResponseEntity<Boolean> checkFavorite(@PathVariable String symbol,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        try {
            return ResponseEntity.ok(favoriteService.isFavorite(resolveUserId(principal), symbol));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
