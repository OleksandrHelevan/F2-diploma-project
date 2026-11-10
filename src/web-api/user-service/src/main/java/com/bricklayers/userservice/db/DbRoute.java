package com.bricklayers.userservice.db;

public enum DbRoute {
    PRIMARY("primary"),
    PRIMARY_SINGLE("primary-single"),
    PRIMARY_EXPLICIT("primary-explicit"),
    PRIMARY_STICKY("primary-sticky"),
    PRIMARY_FALLBACK("primary-fallback"),
    REPLICA("replica");

    private final String header;

    DbRoute(String header) {
        this.header = header;
    }

    public String header() {
        return header;
    }

    public boolean usesReplica() {
        return this == REPLICA;
    }
}
