package com.la_cocina_backend.repositories;

import com.la_cocina_backend.models.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
}
