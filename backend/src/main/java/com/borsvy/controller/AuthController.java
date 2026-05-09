package com.borsvy.controller;

import com.borsvy.model.Subscription;
import com.borsvy.model.User;
import com.borsvy.security.JwtService;
import com.borsvy.security.UserPrincipal;
import com.borsvy.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final int COOKIE_MAX_AGE = 60 * 60 * 24 * 7; // 7 days

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody Map<String, String> body,
            HttpServletResponse response) {
        try {
            String email = body.get("email");
            String password = body.get("password");
            if (email == null || password == null || password.length() < 8) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email and password (min 8 chars) required"));
            }
            User user = userService.register(email.trim().toLowerCase(), password);
            String token = jwtService.generateToken(user.getId(), user.getEmail());
            setAuthCookie(response, token);
            return ResponseEntity.ok(buildUserResponse(user, "FREE"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Registration error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Registration failed"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, String> body,
            HttpServletResponse response) {
        try {
            String email = body.get("email");
            String password = body.get("password");
            String token = userService.login(email.trim().toLowerCase(), password);
            setAuthCookie(response, token);
            Long userId = jwtService.extractUserId(token);
            User user = userService.findById(userId).orElseThrow();
            String status = userService.getSubscription(userId).map(Subscription::getStatus).orElse("FREE");
            return ResponseEntity.ok(buildUserResponse(user, status));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Login error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Login failed"));
        }
    }

    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> googleLogin(
            @RequestBody Map<String, String> body,
            HttpServletResponse response) {
        try {
            String idToken = body.get("credential");
            String token = userService.loginWithGoogle(idToken);
            setAuthCookie(response, token);
            Long userId = jwtService.extractUserId(token);
            User user = userService.findById(userId).orElseThrow();
            String status = userService.getSubscription(userId).map(Subscription::getStatus).orElse("FREE");
            return ResponseEntity.ok(buildUserResponse(user, status));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Google login error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Google login failed"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        User user = userService.findById(principal.getId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }
        String status = userService.getSubscription(principal.getId()).map(Subscription::getStatus).orElse("FREE");
        return ResponseEntity.ok(buildUserResponse(user, status));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("auth_token", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok().build();
    }

    private void setAuthCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("auth_token", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(COOKIE_MAX_AGE);
        // cookie.setSecure(true); // enable in prod with HTTPS
        response.addCookie(cookie);
    }

    private Map<String, Object> buildUserResponse(User user, String subscriptionStatus) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("email", user.getEmail());
        result.put("subscriptionStatus", subscriptionStatus);
        result.put("isPro", "PRO".equals(subscriptionStatus));
        return result;
    }
}
