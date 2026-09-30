package com.bricklayers.userservice.exception;

import com.bricklayers.userservice.controller.UserController;
import com.bricklayers.userservice.dto.CreateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final MessageSource messageSource = createMessageSource();
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource);

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @Test
    void handleUserNotFound_returnsNotFoundResponse() {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/" + userId);

        ResponseEntity<ErrorResponse> response = handler.handleUserNotFound(
                new UserNotFoundException("user.not.found", userId), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("User with id {0} was not found");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/users/" + userId);
    }

    @Test
    void handleUserAlreadyExists_returnsConflictResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");

        ResponseEntity<ErrorResponse> response = handler.handleUserAlreadyExists(
                new UserAlreadyExistsException("user.already.exists.email", "builder@example.com"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("User with email {0} already exists");
    }

    @Test
    void handleValidation_returnsBadRequestWithDetails() throws NoSuchMethodException {
        CreateUserRequest request = new CreateUserRequest(
                "",
                "bad-email",
                "A".repeat(101),
                "B".repeat(101),
                null,
                null);

        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "request");
        bindingResult.addError(new FieldError("request", "username", "Username must not be blank"));
        bindingResult.addError(new FieldError("request", "email", "Invalid email format"));

        Method method = UserController.class.getDeclaredMethod("create", CreateUserRequest.class);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(method, 0), bindingResult);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest("POST", "/api/v1/users");

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Validation failed for input data");
        assertThat(response.getBody().details()).contains(
                "username: Username must not be blank",
                "email: Invalid email format");
    }

    @Test
    void handleAccessDenied_returnsForbiddenResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");

        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(
                new AccessDeniedException("Forbidden"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Insufficient permissions to perform this action");
    }

    @Test
    void handleGeneric_returnsInternalServerErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(new RuntimeException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("Internal server error");
    }

    private MessageSource createMessageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }
}
