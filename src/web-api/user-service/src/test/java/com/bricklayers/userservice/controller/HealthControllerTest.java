package com.bricklayers.userservice.controller;

import com.bricklayers.userservice.db.NodeHealth;
import com.bricklayers.userservice.db.ReplicaPool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    @Test
    void singlePrimary_upAndDown() {
        assertThat(health(node(true), null)).satisfies(r -> {
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(r.getBody()).containsEntry("status", "UP").doesNotContainKey("replica-1");
        });
        assertThat(health(node(false), null).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void replicaDown_isDegradedButHealthy() {
        ResponseEntity<Map<String, String>> response = health(node(true), node(false));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "DEGRADED").containsEntry("replica-1", "DOWN");
    }

    @Test
    void primaryDown_staysInTheLoadBalancerInReadOnlyMode() {
        ResponseEntity<Map<String, String>> response = health(node(false), node(true));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "READ_ONLY").containsEntry("primary", "DOWN");
    }

    @Test
    void bothDown_isUnavailable() {
        assertThat(health(node(false), node(false)).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(health(node(true), node(true)).getBody()).containsEntry("status", "UP");
    }

    @SuppressWarnings("unchecked")
    private static ResponseEntity<Map<String, String>> health(NodeHealth primary, NodeHealth replica) {
        ObjectProvider<ReplicaPool> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(replica == null ? null : new ReplicaPool(List.of(
                new ReplicaPool.Replica("replica-1", mock(DataSource.class), replica))));
        return new HealthController(primary, provider).health();
    }

    private static NodeHealth node(boolean up) {
        NodeHealth health = new NodeHealth("node", mock(DataSource.class));
        if (!up) {
            health.markDown(new SQLException("Connection refused"));
        }
        return health;
    }
}
