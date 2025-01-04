package com.la_cocina_backend.models;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "menu_items")
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private String imageUrl;
    private Boolean isAvailable = true;

    // constructors, getters, setters (or use Lombok)
}
