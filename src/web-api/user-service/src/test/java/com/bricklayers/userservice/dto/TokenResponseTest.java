package com.bricklayers.userservice.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesWithOAuthTokenFieldNames() throws Exception {
        TokenResponse response = new TokenResponse("jwt-value", "Bearer", 300);

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).isEqualTo(
                "{\"access_token\":\"jwt-value\",\"token_type\":\"Bearer\",\"expires_in\":300}");
    }
}
