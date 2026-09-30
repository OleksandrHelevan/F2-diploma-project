package com.bricklayers.projectsservice.model;
/*
  @author   oleksandrHelevan
  @project   market-service
  @class  Product
  @version  1.0.0
  @since 20.09.2026 - 20.46
*/

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@AllArgsConstructor
@Getter
@Setter
public class Product {
    private UUID id;
    private UUID supplierId;
    private String name;
    private String description;
    private String category;
    private String unit;
    private double price;
    private int amount;
    private boolean isAvailable;
}