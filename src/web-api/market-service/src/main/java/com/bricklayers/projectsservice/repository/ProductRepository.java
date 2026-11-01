package com.bricklayers.projectsservice.repository;

import com.bricklayers.projectsservice.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/*
  @author   oleksandr
  @project   F2-diploma-project
  @class  ProductRepository
  @version  1.0.0 
  @since 31.10.2026 - 11.17
*/
public interface ProductRepository extends JpaRepository<Product, UUID> {
}
