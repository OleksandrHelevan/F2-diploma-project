package com.bricklayers.userservice.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class NodeHealth {

    private final String name;
    private final DataSource probe;
    private final AtomicBoolean up = new AtomicBoolean(true);

    public NodeHealth(DataSource probe) {
        this("replica", probe);
    }

    public NodeHealth(String name, DataSource probe) {
        this.name = name;
        this.probe = probe;
    }

    public static NodeHealth probing(String name, String jdbcUrl, String username, String password) {
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        return new NodeHealth(name, new DriverManagerDataSource(
                jdbcUrl + separator + "connectTimeout=1&socketTimeout=2&ApplicationName=health-probe", username, password));
    }

    public String name() {
        return name;
    }

    public boolean isUp() {
        return up.get();
    }

    public void markDown(Throwable cause) {
        if (up.compareAndSet(true, false)) {
            log.warn("Database node {} is unavailable: {}", name, cause.toString());
        }
    }

    public void onConnectionFailure(SQLException failure) {
        if (failure.getCause() != null) {
            markDown(failure);
        }
    }

    public boolean probe() {
        try (Connection connection = probe.getConnection()) {
            if (connection.isValid(1)) {
                if (up.compareAndSet(false, true)) {
                    log.info("Database node {} is available again", name);
                }
                return true;
            }
            markDown(new SQLException("connection is not valid"));
        } catch (SQLException exception) {
            markDown(exception);
        }
        return false;
    }
}
