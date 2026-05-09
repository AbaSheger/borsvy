package com.borsvy.service;

import com.borsvy.model.Subscription;
import com.borsvy.model.User;
import com.borsvy.repository.SubscriptionRepository;
import com.borsvy.repository.UserRepository;
import com.borsvy.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private JwtService jwtService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final RestTemplate restTemplate = new RestTemplate();

    @Transactional
    public User register(String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = new User(email, passwordEncoder.encode(rawPassword));
        user = userRepository.save(user);
        subscriptionRepository.save(new Subscription(user.getId()));
        return user;
    }

    public String login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        if (user.getPassword() == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        return jwtService.generateToken(user.getId(), user.getEmail());
    }

    @Transactional
    public String loginWithGoogle(String idToken) {
        // Verify token with Google tokeninfo endpoint
        String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> tokenInfo = restTemplate.getForObject(url, Map.class);
            if (tokenInfo == null || tokenInfo.containsKey("error")) {
                throw new IllegalArgumentException("Invalid Google token");
            }
            String email = (String) tokenInfo.get("email");
            String googleId = (String) tokenInfo.get("sub");

            Optional<User> byGoogle = userRepository.findByGoogleId(googleId);
            User user;
            if (byGoogle.isPresent()) {
                user = byGoogle.get();
            } else {
                Optional<User> byEmail = userRepository.findByEmail(email);
                if (byEmail.isPresent()) {
                    user = byEmail.get();
                    user.setGoogleId(googleId);
                    user = userRepository.save(user);
                } else {
                    user = new User(email, null);
                    user.setGoogleId(googleId);
                    user = userRepository.save(user);
                    subscriptionRepository.save(new Subscription(user.getId()));
                }
            }
            return jwtService.generateToken(user.getId(), user.getEmail());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Google authentication failed");
        }
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<Subscription> getSubscription(Long userId) {
        return subscriptionRepository.findByUserId(userId);
    }

    public boolean isPro(Long userId) {
        return subscriptionRepository.findByUserId(userId)
                .map(Subscription::isPro)
                .orElse(false);
    }
}
