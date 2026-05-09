package com.borsvy.controller;

import com.borsvy.model.PriceAlert;
import com.borsvy.security.UserPrincipal;
import com.borsvy.service.PriceAlertService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class PriceAlertController {

    private static final Long GUEST_USER_ID = 0L;

    private final PriceAlertService priceAlertService;

    public PriceAlertController(PriceAlertService priceAlertService) {
        this.priceAlertService = priceAlertService;
    }

    @GetMapping
    public ResponseEntity<List<PriceAlert>> getAlerts(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(priceAlertService.getAlerts(resolveUserId(principal)));
    }

    @PostMapping
    public ResponseEntity<?> addAlert(@RequestBody PriceAlert alert,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        try {
            return ResponseEntity.ok(priceAlertService.addAlert(resolveUserId(principal), alert));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<PriceAlert> toggleAlert(@PathVariable Long id,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return priceAlertService.toggleAlert(resolveUserId(principal), id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PatchMapping("/{id}/triggered")
    public ResponseEntity<PriceAlert> markTriggered(@PathVariable Long id,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return priceAlertService.markTriggered(resolveUserId(principal), id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeAlert(@PathVariable Long id,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        boolean removed = priceAlertService.removeAlert(resolveUserId(principal), id);
        return removed ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    private Long resolveUserId(UserPrincipal principal) {
        return principal != null ? principal.getId() : GUEST_USER_ID;
    }
}
