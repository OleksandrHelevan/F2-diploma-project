package com.bricklayers.userservice.db;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {

    private final DataSource primary;
    private final NodeHealth primaryHealth;
    private final ReplicaPool replicas;
    private final ReadYourWritesTracker tracker;

    public ReadWriteRoutingDataSource(DataSource primary, NodeHealth primaryHealth, ReplicaPool replicas,
                                      ReadYourWritesTracker tracker) {
        this.primary = primary;
        this.primaryHealth = primaryHealth;
        this.replicas = replicas == null ? new ReplicaPool(List.of()) : replicas;
        this.tracker = tracker;
        Map<Object, Object> targets = new HashMap<>();
        targets.put("primary", primary);
        this.replicas.replicas().forEach(replica -> targets.put(replica.name(), replica.dataSource()));
        setTargetDataSources(targets);
        setDefaultTargetDataSource(primary);
        afterPropertiesSet();
    }

    public DbRoute decide() {
        boolean readOnly = TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        if (!readOnly) {
            tracker.onPrimaryTransaction();
            return DbRoute.PRIMARY;
        }
        if (replicas.isEmpty()) {
            return DbRoute.PRIMARY_SINGLE;
        }
        if (ReadRoutingContext.isExplicitPrimary()) {
            return DbRoute.PRIMARY_EXPLICIT;
        }
        if (ReadRoutingContext.isStickyPrimary()) {
            return DbRoute.PRIMARY_STICKY;
        }
        return DbRoute.REPLICA;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return "primary";
    }

    @Override
    public Connection getConnection() throws SQLException {
        DbRoute route = decide();
        if (route.usesReplica()) {
            ReplicaPool.Replica replica = replicas.nextAvailable();
            if (replica != null) {
                try {
                    Connection connection = replica.dataSource().getConnection();
                    RouteReporter.report(replica.name());
                    return connection;
                } catch (SQLException exception) {
                    replica.health().onConnectionFailure(exception);
                }
            }
            route = DbRoute.PRIMARY_FALLBACK;
        }
        RouteReporter.report(route.header());
        if (primaryHealth != null && !primaryHealth.isUp()) {
            throw new SQLTransientConnectionException("Primary database is unavailable: the service is in read-only mode");
        }
        try {
            return primary.getConnection();
        } catch (SQLException exception) {
            if (primaryHealth != null) {
                primaryHealth.onConnectionFailure(exception);
            }
            throw exception;
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }
}
