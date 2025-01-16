package com.la_cocina_backend.models;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name = "customizations")
@Data
public class Customization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    private String name;
    private String type;  // "default" or "addon"
    private BigDecimal price;
    private Boolean isDefault;

    // Getters and Setters
}