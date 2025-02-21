package com.bullpen_pizza_backend.services;

import com.bullpen_pizza_backend.models.Customization;
import com.bullpen_pizza_backend.models.MenuItem;
import com.bullpen_pizza_backend.repositories.CustomizationRepository;
import com.bullpen_pizza_backend.repositories.MenuItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomizationService {

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private CustomizationRepository customizationRepository;

    public List<Customization> getCustomizationsForItem(Long menuItemId) {
        return customizationRepository.findByMenuItemId(menuItemId);
    }

    public MenuItem getMenuItem(Long menuItemId) {
        return menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new RuntimeException("Menu item not found"));
    }
}
