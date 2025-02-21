package com.bullpen_pizza_backend.repositories;

import com.bullpen_pizza_backend.models.Customization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomizationRepository extends JpaRepository<Customization, Long> {
    List<Customization> findByMenuItemId(Long menuItemId);
}