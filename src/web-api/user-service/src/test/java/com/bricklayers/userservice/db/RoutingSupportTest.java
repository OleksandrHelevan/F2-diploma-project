package com.bricklayers.userservice.db;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutingSupportTest {

    @AfterEach
    void tearDown() {
        ReadRoutingContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void explicitPrimaryScopesNest() {
        ReadRoutingContext.onPrimary(() -> {
            ReadRoutingContext.onPrimary(() -> null);
            assertThat(ReadRoutingContext.isExplicitPrimary()).isTrue();
            return null;
        });

        assertThat(ReadRoutingContext.isExplicitPrimary()).isFalse();
    }

    @Test
    void aspectRoutesTheAnnotatedMethodToThePrimary_andRestoresTheContextOnFailure() throws Throwable {
        ReadFromPrimaryAspect aspect = new ReadFromPrimaryAspect();
        ProceedingJoinPoint ok = mock(ProceedingJoinPoint.class);
        when(ok.proceed()).thenAnswer(invocation -> ReadRoutingContext.isExplicitPrimary());
        ProceedingJoinPoint failing = mock(ProceedingJoinPoint.class);
        when(failing.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThat(aspect.routeToPrimary(ok)).isEqualTo(true);
        assertThatThrownBy(() -> aspect.routeToPrimary(failing)).hasMessage("boom");
        assertThat(ReadRoutingContext.isExplicitPrimary()).isFalse();
    }

    @Test
    void interceptorMakesAUserStickyForTheRequest_andClearsItAfterwards() {
        ReadYourWritesTracker tracker = mock(ReadYourWritesTracker.class);
        when(tracker.isSticky("kc-admin")).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                Jwt.withTokenValue("t").header("alg", "none").subject("kc-admin").build()));
        ReadYourWritesInterceptor interceptor = new ReadYourWritesInterceptor(tracker);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(ReadRoutingContext.isStickyPrimary()).isTrue();

        interceptor.afterCompletion(request, response, new Object(), null);
        assertThat(ReadRoutingContext.isStickyPrimary()).isFalse();
    }

    @Test
    void replicaHealthFollowsTheProbe() throws SQLException {
        DataSource replica = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(replica.getConnection()).thenThrow(new SQLException("refused")).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(true);
        NodeHealth health = new NodeHealth(replica);

        assertThat(health.probe()).isFalse();
        assertThat(health.isUp()).isFalse();
        assertThat(health.probe()).isTrue();
        assertThat(health.isUp()).isTrue();
    }

    @Test
    void invalidReplicaConnectionCountsAsDown() throws SQLException {
        DataSource replica = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(replica.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(false);

        assertThat(new NodeHealth(replica).probe()).isFalse();
    }

    @Test
    void routeReporterIgnoresCallsOutsideARequest() {
        RouteReporter.report(DbRoute.REPLICA);
        assertThat(DbRoute.PRIMARY_STICKY.header()).isEqualTo("primary-sticky");
        assertThat(new ReadYourWritesTracker(null, Duration.ofSeconds(3)).ttl()).hasSeconds(3);
    }
}
