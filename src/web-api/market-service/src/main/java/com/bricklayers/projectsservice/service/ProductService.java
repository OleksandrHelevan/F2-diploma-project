package com.bricklayers.projectsservice.service;
/*
  @author   oleksandrHelevan
  @project   market-service
  @class  ProductService
  @version  1.0.0
*/

import com.bricklayers.projectsservice.model.Product;

import java.util.List;
import java.util.UUID;

public interface ProductService {

    Product create(Product product);

    Product getById(UUID id);

    List<Product> getAll();

    Product update(UUID id, Product product);

    Product patch(UUID id, Product product);

    void delete(UUID id);
}