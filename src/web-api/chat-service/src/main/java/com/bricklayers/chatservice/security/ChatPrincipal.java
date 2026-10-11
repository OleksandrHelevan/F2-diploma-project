package com.bricklayers.chatservice.security;

import java.security.Principal;

public record ChatPrincipal(UserContext user) implements Principal {
    @Override
    public String getName() {
        return user.userId();
    }
}
