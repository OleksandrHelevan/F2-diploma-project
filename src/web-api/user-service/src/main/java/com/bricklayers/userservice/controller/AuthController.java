package com.bricklayers.userservice.controller;

import com.bricklayers.userservice.dto.LoginRequest;
import com.bricklayers.userservice.dto.RegisterRequest;
import com.bricklayers.userservice.dto.TokenResponse;
import com.bricklayers.userservice.dto.UserDto;
import com.bricklayers.userservice.service.AuthService;
import com.bricklayers.userservice.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
  @author   user
  @project   F2-diploma-project
  @class  AuthController
  @version  1.0.0 
  @since 09.10.2026 - 00.04
*/
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest request) {
        UserDto created = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
