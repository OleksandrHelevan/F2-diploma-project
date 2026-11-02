package com.bricklayers.projectsservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
  @author   oleksandr
  @project   F2-diploma-project
  @class  RoleController
  @version  1.0.0 
  @since 02.11.2026 - 22.06
*/
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    @GetMapping("/superadmin")
    public ResponseEntity<String> getForSuperadmin() {
        return new ResponseEntity<>("Superadmin", HttpStatus.OK);
    }
    @GetMapping("/foreman")
    public ResponseEntity<String> getForForeman() {
        return new ResponseEntity<>("Foreman", HttpStatus.OK);
    }
    @GetMapping("/builder")
    public ResponseEntity<String> getForBuilder() {
        return new ResponseEntity<>("Builder", HttpStatus.OK);
    }
    @GetMapping("/customer")
    public ResponseEntity<String> getForCustomer() {
        return new ResponseEntity<>("Customer", HttpStatus.OK);
    }
    @GetMapping("/supplier")
    public ResponseEntity<String> getForSupplier() {
        return new ResponseEntity<>("Supplier", HttpStatus.OK);
    }
    @GetMapping("/all")
    public ResponseEntity<String> getForAll() {
        return new ResponseEntity<>("All", HttpStatus.OK);
    }
}
