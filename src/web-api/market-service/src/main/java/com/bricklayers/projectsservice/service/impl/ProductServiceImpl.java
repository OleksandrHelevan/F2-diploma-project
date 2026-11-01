package com.bricklayers.projectsservice.service.impl;

import com.bricklayers.projectsservice.model.Product;
import com.bricklayers.projectsservice.repository.ProductRepository;
import com.bricklayers.projectsservice.service.ProductService;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @PostConstruct
    void init() {
        productRepository.deleteAll();

        List.of(
                new Product(null, UUID.randomUUID(), "Цемент М400",
                        "Портландцемент М400, мішок 50 кг", "Сипучі матеріали",
                        "мішок", 210.0, 500, true),
                new Product(null, UUID.randomUUID(), "Цегла керамічна рядова",
                        "Одинарна керамічна цегла М150", "Стінові матеріали",
                        "шт", 12.5, 10000, true),
                new Product(null, UUID.randomUUID(), "Арматура А500С 12мм",
                        "Арматурний прут періодичного профілю, довжина 12 м",
                        "Металопрокат", "прут", 350.0, 800, true),
                new Product(null, UUID.randomUUID(), "Пісок будівельний",
                        "Пісок кар'єрний, фракція 0-5 мм", "Сипучі матеріали",
                        "м3", 450.0, 120, false),
                new Product(null, UUID.randomUUID(), "Гіпсокартон 12.5мм",
                        "Гіпсокартонний лист стандартний, 1200x2500 мм",
                        "Оздоблювальні матеріали", "лист", 185.0, 300, true)
        ).forEach(this::create);
    }

    @Override
    @Transactional
    public Product create(Product product) {
        product.setId(UUID.randomUUID());
        return productRepository.save(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Product getById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product with id " + id + " not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getAll() {
        return productRepository.findAll();
    }

    @Override
    @Transactional
    public Product update(UUID id, Product product) {
        Product existing = getById(id);
        existing.setSupplierId(product.getSupplierId());
        existing.setName(product.getName());
        existing.setDescription(product.getDescription());
        existing.setCategory(product.getCategory());
        existing.setUnit(product.getUnit());
        existing.setPrice(product.getPrice());
        existing.setAmount(product.getAmount());
        existing.setAvailable(product.isAvailable());
        return productRepository.save(existing);
    }

    @Override
    @Transactional
    public Product patch(UUID id, Product product) {
        Product existing = getById(id);

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

        return productRepository.save(existing);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!productRepository.existsById(id)) {
            throw new EntityNotFoundException("Product with id " + id + " not found");
        }
        productRepository.deleteById(id);
    }
}