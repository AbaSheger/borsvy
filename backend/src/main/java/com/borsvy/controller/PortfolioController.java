package com.borsvy.controller;

import com.borsvy.model.PortfolioHolding;
import com.borsvy.security.UserPrincipal;
import com.borsvy.service.PortfolioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private static final Long GUEST_USER_ID = 0L;

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/holdings")
    public ResponseEntity<List<PortfolioHolding>> getHoldings(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(portfolioService.getHoldings(resolveUserId(principal)));
    }

    @PostMapping("/holdings")
    public ResponseEntity<?> addHolding(@RequestBody PortfolioHolding holding,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        try {
            return ResponseEntity.ok(portfolioService.addHolding(resolveUserId(principal), holding));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/holdings/{id}")
    public ResponseEntity<Void> removeHolding(@PathVariable Long id,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        boolean removed = portfolioService.removeHolding(resolveUserId(principal), id);
        return removed ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    private Long resolveUserId(UserPrincipal principal) {
        return principal != null ? principal.getId() : GUEST_USER_ID;
    }
}
