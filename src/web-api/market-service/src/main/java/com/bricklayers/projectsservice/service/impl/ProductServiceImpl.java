package com.bricklayers.projectsservice.service.impl;
/*
  @author   oleksandrHelevan
  @project   v
  @class  ProductServiceImpl
  @version  1.0.0
  @since 20.09.2026 - 20.58
*/

import com.bricklayers.projectsservice.model.Product;
import com.bricklayers.projectsservice.service.ProductService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ProductServiceImpl implements ProductService {

    private final List<Product> products = new ArrayList<>(List.of(
            new Product(UUID.randomUUID(), UUID.randomUUID(), "Цемент М400",
                    "Портландцемент М400, мішок 50 кг", "Сипучі матеріали",
                    "мішок", 210.0, 500, true
            ),
            new Product(UUID.randomUUID(), UUID.randomUUID(), "Цегла керамічна рядова",
                    "Одинарна керамічна цегла М150", "Стінові матеріали",
                    "шт", 12.5, 10000, true
            ),
            new Product(UUID.randomUUID(), UUID.randomUUID(), "Арматура А500С 12мм",
                    "Арматурний прут періодичного профілю, довжина 12 м",
                    "Металопрокат", "прут", 350.0, 800, true
            ),
            new Product(UUID.randomUUID(), UUID.randomUUID(), "Пісок будівельний",
                    "Пісок кар'єрний, фракція 0-5 мм", "Сипучі матеріали",
                    "м3", 450.0, 120, false
            ),
            new Product(UUID.randomUUID(), UUID.randomUUID(), "Гіпсокартон 12.5мм",
                    "Гіпсокартонний лист стандартний, 1200x2500 мм",
                    "Оздоблювальні матеріали", "лист", 185.0, 300, true
            )
    ));

    @Override
    public Product create(Product product) {
        product.setId(UUID.randomUUID());
        products.add(product);
        return product;
    }

    @Override
    public Product getById(UUID id) {
        return findByIdOrThrow(id);
    }

    @Override
    public List<Product> getAll() {
        return new ArrayList<>(products);
    }

    @Override
    public Product update(UUID id, Product product) {
        Product existing = findByIdOrThrow(id);
        existing.setSupplierId(product.getSupplierId());
        existing.setName(product.getName());
        existing.setDescription(product.getDescription());
        existing.setCategory(product.getCategory());
        existing.setUnit(product.getUnit());
        existing.setPrice(product.getPrice());
        existing.setAmount(product.getAmount());
        existing.setAvailable(product.isAvailable());
        return existing;
    }

    @Override
    public Product patch(UUID id, Product product) {
        Product existing = findByIdOrThrow(id);

        if (product.getName() != null && !product.getName().isBlank()) {
            existing.setName(product.getName());
        }
        if (product.getDescription() != null && !product.getDescription().isBlank()) {
            existing.setDescription(product.getDescription());
        }
        if (product.getCategory() != null && !product.getCategory().isBlank()) {
            existing.setCategory(product.getCategory());
        }
        if (product.getUnit() != null && !product.getUnit().isBlank()) {
            existing.setUnit(product.getUnit());
        }
        if (product.getPrice() > 0) {
            existing.setPrice(product.getPrice());
        }
        if (product.getAmount() > 0) {
            existing.setAmount(product.getAmount());
        }
        existing.setAvailable(product.isAvailable());

        return existing;
    }

    @Override
    public void delete(UUID id) {
        Product existing = findByIdOrThrow(id);
        products.remove(existing);
    }

    private Product findByIdOrThrow(UUID id) {
        return products.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Product with id=" + id + " not found"));
    }

}