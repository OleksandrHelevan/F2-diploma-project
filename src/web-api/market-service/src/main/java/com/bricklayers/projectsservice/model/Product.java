package com.bricklayers.projectsservice.model;
/*
  @author   oleksandrHelevan
  @project   market-service
  @class  Product
  @version  1.0.0
  @since 20.09.2026 - 20.46
*/

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "products")
    public class Product extends AuditTable {
    @Id
    private UUID id;
    private UUID supplierId;
    private String name;
    private String description;
    private String category;
    private String unit;
    private double price;
    private int amount;
    private boolean available;
}