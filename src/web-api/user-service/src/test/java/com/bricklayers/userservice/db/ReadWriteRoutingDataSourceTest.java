package com.bricklayers.userservice.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadWriteRoutingDataSourceTest {

    private final DataSource primary = mock(DataSource.class);
    private final DataSource replica = mock(DataSource.class);
    private final Connection primaryConnection = mock(Connection.class);
    private final Connection replicaConnection = mock(Connection.class);
    private final NodeHealth health = new NodeHealth("replica-1", replica);
    private final NodeHealth primaryHealth = new NodeHealth("primary", primary);
    private final ReadYourWritesTracker tracker = new ReadYourWritesTracker(null, null);
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private ReadWriteRoutingDataSource routing;

    @BeforeEach
    void setUp() throws SQLException {
        when(primary.getConnection()).thenReturn(primaryConnection);
        when(replica.getConnection()).thenReturn(replicaConnection);
        routing = new ReadWriteRoutingDataSource(primary, primaryHealth,
                new ReplicaPool(List.of(new ReplicaPool.Replica("replica-1", replica, health))), tracker);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest(), response));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        ReadRoutingContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void writeTransaction_goesToPrimary() throws SQLException {
        inTransaction(false);

        assertThat(routing.getConnection()).isSameAs(primaryConnection);
        assertThat(response.getHeader(RouteReporter.HEADER)).isEqualTo("primary");
        verify(replica, never()).getConnection();
    }

    @Test
    void readOutsideATransaction_goesToPrimary() throws SQLException {
        assertThat(routing.getConnection()).isSameAs(primaryConnection);
        assertThat(routing.decide()).isEqualTo(DbRoute.PRIMARY);
    }

    @Test
    void readOnlyTransaction_goesToReplica() throws SQLException {
        inTransaction(true);

        assertThat(routing.getConnection()).isSameAs(replicaConnection);
        assertThat(response.getHeader(RouteReporter.HEADER)).isEqualTo("replica-1");
    }

    @Test
    void explicitPrimaryRead_goesToPrimary() throws SQLException {
        inTransaction(true);

        Connection connection = ReadRoutingContext.onPrimary(() -> {
            try {
                return routing.getConnection();
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        });

        assertThat(connection).isSameAs(primaryConnection);
        assertThat(response.getHeader(RouteReporter.HEADER)).isEqualTo("primary-explicit");
        assertThat(ReadRoutingContext.isExplicitPrimary()).isFalse();
    }

    @Test
    void stickyUserAfterAWrite_readsFromPrimary() throws SQLException {
        inTransaction(true);
        ReadRoutingContext.setStickyPrimary(true);

        assertThat(routing.getConnection()).isSameAs(primaryConnection);
        assertThat(response.getHeader(RouteReporter.HEADER)).isEqualTo("primary-sticky");
    }

    @Test
    void replicaMarkedDown_fallsBackToPrimary() throws SQLException {
        inTransaction(true);
        health.markDown(new SQLException("replica stopped"));

        assertThat(routing.getConnection()).isSameAs(primaryConnection);
        assertThat(response.getHeader(RouteReporter.HEADER)).isEqualTo("primary-fallback");
        verify(replica, never()).getConnection();
    }

    @Test
    void replicaConnectionFailure_fallsBackToPrimaryAndMarksTheReplicaDown() throws SQLException {
        inTransaction(true);
        when(replica.getConnection()).thenThrow(new SQLException("Connection is not available", new java.net.ConnectException("Connection refused")));

        assertThat(routing.getConnection("ignored", "ignored")).isSameAs(primaryConnection);
        assertThat(response.getHeader(RouteReporter.HEADER)).isEqualTo("primary-fallback");
        assertThat(health.isUp()).isFalse();
        assertThat(routing.decide()).isEqualTo(DbRoute.REPLICA);
    }

    @Test
    void withoutAReplica_everyReadGoesToTheSinglePrimary() throws SQLException {
        ReadWriteRoutingDataSource single = new ReadWriteRoutingDataSource(primary, primaryHealth, null, tracker);
        inTransaction(true);

        assertThat(single.getConnection()).isSameAs(primaryConnection);
        assertThat(response.getHeader(RouteReporter.HEADER)).isEqualTo("primary-single");
    }

    @Test
    void severalConnectionsInOneRequest_areAllReported() throws SQLException {
        inTransaction(true);
        routing.getConnection();
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        routing.getConnection();

        assertThat(response.getHeader(RouteReporter.HEADER)).isEqualTo("replica-1,primary");
    }

    @Test
    void readsAreSpreadRoundRobinOverHealthyReplicas_andSkipAReplicaThatIsDown() throws SQLException {
        DataSource second = mock(DataSource.class);
        Connection secondConnection = mock(Connection.class);
        when(second.getConnection()).thenReturn(secondConnection);
        NodeHealth secondHealth = new NodeHealth("replica-2", second);
        ReadWriteRoutingDataSource pool = new ReadWriteRoutingDataSource(primary, primaryHealth, new ReplicaPool(List.of(
                new ReplicaPool.Replica("replica-1", replica, health),
                new ReplicaPool.Replica("replica-2", second, secondHealth))), tracker);
        inTransaction(true);

        assertThat(pool.getConnection()).isSameAs(replicaConnection);
        assertThat(pool.getConnection()).isSameAs(secondConnection);
        assertThat(pool.getConnection()).isSameAs(replicaConnection);

        health.markDown(new SQLException("stopped"));
        assertThat(pool.getConnection()).isSameAs(secondConnection);
        assertThat(pool.getConnection()).isSameAs(secondConnection);

        secondHealth.markDown(new SQLException("stopped"));
        assertThat(pool.getConnection()).isSameAs(primaryConnection);
    }

    @Test
    void primaryKnownDown_writesFailFastInReadOnlyMode() throws SQLException {
        inTransaction(false);
        primaryHealth.markDown(new SQLException("stopped"));

        assertThatThrownBy(() -> routing.getConnection())
                .isInstanceOf(java.sql.SQLTransientConnectionException.class)
                .hasMessageContaining("read-only mode");
        verify(primary, never()).getConnection();
    }

    @Test
    void primaryConnectFailure_marksItDown_butPoolExhaustionDoesNot() throws SQLException {
        inTransaction(false);
        when(primary.getConnection())
                .thenThrow(new SQLException("Connection is not available, request timed out after 3000ms"))
                .thenThrow(new SQLException("Connection is not available", new java.net.ConnectException("refused")));

        assertThatThrownBy(() -> routing.getConnection()).isInstanceOf(SQLException.class);
        assertThat(primaryHealth.isUp()).isTrue();
        assertThatThrownBy(() -> routing.getConnection()).isInstanceOf(SQLException.class);
        assertThat(primaryHealth.isUp()).isFalse();
    }

    private static void inTransaction(boolean readOnly) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(readOnly);
    }
}
