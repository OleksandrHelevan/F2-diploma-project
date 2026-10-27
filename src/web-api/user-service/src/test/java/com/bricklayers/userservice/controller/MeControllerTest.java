//package com.bricklayers.userservice.controller;
//
//import com.bricklayers.userservice.dto.SetPasswordRequest;
//import com.bricklayers.userservice.dto.UserDto;
//import com.bricklayers.userservice.entity.Role;
//import com.bricklayers.userservice.entity.User;
//import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient;
//import com.bricklayers.userservice.service.UserService;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.oauth2.jwt.Jwt;
//import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
//
//import java.util.Optional;
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//
//@ExtendWith(MockitoExtension.class)
//class MeControllerTest {
//
//    @Mock
//    private UserService userService;
//
//    @InjectMocks
//    private MeController meController;
//
//
//    UserDto userDto;
//    UUID userId;
//    @BeforeEach
//    void setUp() {
//        userId = UUID.randomUUID();
//        userDto = new UserDto(
//                userId,
//                "builder",
//                "builder@example.com",
//                "Ivan",
//                "Builder",
//                Role.BUILDER,
//                null,
//                true,
//                null,
//                null);
//    }
//
//    @Test
//    void getMe() {
//    }
//
//}