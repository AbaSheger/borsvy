package com.borsvy.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "favorites", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "symbol"})
})
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private String symbol;
    private String name;
    private double price;
    private double change;
    private double changePercent;
    private LocalDateTime addedAt;

    @Version
    private Long version;

    public Favorite() {}

    public Favorite(Long userId, String symbol, String name, double price, double change, double changePercent) {
        this.userId = userId;
        this.symbol = symbol;
        this.name = name;
        this.price = price;
        this.change = change;
        this.changePercent = changePercent;
        this.addedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public double getChange() { return change; }
    public void setChange(double change) { this.change = change; }

    public double getChangePercent() { return changePercent; }
    public void setChangePercent(double changePercent) { this.changePercent = changePercent; }

    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
