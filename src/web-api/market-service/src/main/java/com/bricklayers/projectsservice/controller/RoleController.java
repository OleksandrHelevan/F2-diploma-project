package com.bricklayers.projectsservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PreAuthorize("hasRole('SUPERADMIN')")
    @GetMapping("/superadmin")
    public ResponseEntity<String> getForSuperadmin() {
        return new ResponseEntity<>("Superadmin", HttpStatus.OK);
    }

    @PreAuthorize("hasAnyRole('FOREMAN', 'SUPERADMIN')")
    @GetMapping("/foreman")
    public ResponseEntity<String> getForForeman() {
        return new ResponseEntity<>("Foreman", HttpStatus.OK);
    }

    @PreAuthorize("hasAnyRole('BUILDER', 'FOREMAN', 'SUPERADMIN')")
    @GetMapping("/builder")
    public ResponseEntity<String> getForBuilder() {
        return new ResponseEntity<>("Builder", HttpStatus.OK);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/customer")
    public ResponseEntity<String> getForCustomer() {
        return new ResponseEntity<>("Customer", HttpStatus.OK);
    }

    @PreAuthorize("hasRole('SUPPLIER')")
    @GetMapping("/supplier")
    public ResponseEntity<String> getForSupplier() {
        return new ResponseEntity<>("Supplier", HttpStatus.OK);
    }
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/all")
    public ResponseEntity<String> getForAll() {
        return new ResponseEntity<>("All", HttpStatus.OK);
    }
}
