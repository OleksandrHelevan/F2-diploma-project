package com.bricklayers.chatservice.exception;

import com.bricklayers.chatservice.dto.ChatEventResponse;
import com.bricklayers.chatservice.entity.ChatEventType;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionHandlersTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/chats/x");

    @Test
    void domainExceptionsKeepStatusAndCode() {
        assertDomain(new ChatNotFoundException("c1"), HttpStatus.NOT_FOUND, "CHAT_NOT_FOUND");
        assertDomain(new MessageNotFoundException("m1"), HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND");
        assertDomain(new UserNotFoundException("u1"), HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
        assertDomain(new ChatArchivedException("c1"), HttpStatus.CONFLICT, "CHAT_ARCHIVED");
        assertDomain(new ChatAccessDeniedException("no"), HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertDomain(new InvalidChatOperationException("bad"), HttpStatus.BAD_REQUEST, "INVALID_OPERATION");
    }

    @Test
    void validationErrorsAreCollectedPerField() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "content", "too long"));
        bindingResult.addError(new FieldError("request", "content", "duplicate"));
        bindingResult.addError(new FieldError("request", "type", null, false, null, null, null));
        MethodParameter parameter = new MethodParameter(
                ExceptionHandlersTest.class.getDeclaredMethod("sample", String.class), 0);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(
                new MethodArgumentNotValidException(parameter, bindingResult), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().fieldErrors()).isEqualTo(Map.of("content", "too long", "type", "invalid"));
    }

    @Test
    void unreadableBodyIsBadRequest() {
        ResponseEntity<ErrorResponse> response = handler.handleUnreadable(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("MALFORMED_REQUEST");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/chats/x");
    }

    @Test
    void responseStatusExceptionUsesReasonOrDefaultPhrase() {
        ResponseEntity<ErrorResponse> withReason = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "nope"), request);
        ResponseEntity<ErrorResponse> withoutReason = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED), request);

        assertThat(withReason.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(withReason.getBody().code()).isEqualTo("FORBIDDEN");
        assertThat(withReason.getBody().message()).isEqualTo("nope");
        assertThat(withoutReason.getBody().status()).isEqualTo(401);
        assertThat(withoutReason.getBody().message()).isEqualTo("Unauthorized");
    }

    @Test
    void unexpectedExceptionIsInternalError() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(new IllegalStateException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("boom");
    }

    @Test
    void webSocketHandlerWrapsMessageIntoErrorEvent() {
        ChatEventResponse event = new WebSocketExceptionHandler().handle(new ChatAccessDeniedException("denied"));

        assertThat(event.type()).isEqualTo(ChatEventType.ERROR);
        assertThat(event.payload()).isEqualTo("denied");
        assertThat(event.at()).isNotNull();
    }

    private void assertDomain(ChatServiceException exception, HttpStatus status, String code) {
        ResponseEntity<ErrorResponse> response = handler.handleDomain(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody().status()).isEqualTo(status.value());
        assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().message()).isEqualTo(exception.getMessage());
        assertThat(response.getBody().path()).isEqualTo("/api/v1/chats/x");
        assertThat(response.getBody().fieldErrors()).isNull();
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @SuppressWarnings("unused")
    private void sample(String value) {
    }
}
