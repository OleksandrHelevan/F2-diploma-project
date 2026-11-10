package com.bricklayers.userservice.db;

import com.bricklayers.userservice.dto.UserDto;
import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.repository.UserRepository;
import com.bricklayers.userservice.service.UserService;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.datasource.replica.enabled=true",
        "app.datasource.read-your-writes-ttl=0s",
        "app.cache.enabled=false",
        "keycloak.sync.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReadWriteRoutingIT {

    @Container
    static final PostgreSQLContainer PRIMARY = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static final PostgreSQLContainer REPLICA = new PostgreSQLContainer("postgres:16-alpine");

    private static final UUID PRIMARY_ONLY = UUID.randomUUID();
    private static final UUID REPLICA_ONLY = UUID.randomUUID();

    @DynamicPropertySource
    static void databases(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PRIMARY::getJdbcUrl);
        registry.add("spring.datasource.username", PRIMARY::getUsername);
        registry.add("spring.datasource.password", PRIMARY::getPassword);
        registry.add("app.datasource.replica.url", REPLICA::getJdbcUrl);
        registry.add("app.datasource.replica.username", REPLICA::getUsername);
        registry.add("app.datasource.replica.password", REPLICA::getPassword);
    }

    @BeforeAll
    static void prepareReplicaSchemaAndDistinctData() throws Exception {
        DriverManagerDataSource replica = new DriverManagerDataSource(
                REPLICA.getJdbcUrl(), REPLICA.getUsername(), REPLICA.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(replica);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        insertUser(new JdbcTemplate(replica), REPLICA_ONLY, "replica-only");
    }

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ReplicationStatusService replicationStatusService;

    @Test
    @Order(1)
    void eventualRead_readOnlyTransaction_isServedByTheReplica() {
        insertUser(jdbcTemplate, PRIMARY_ONLY, "primary-only");

        List<String> usernames = userService.getUsers(0, 50).map(UserDto::username).getContent();

        assertThat(usernames).contains("replica-only").doesNotContain("primary-only");
    }

    @Test
    @Order(2)
    void strictRead_readFromPrimary_isServedByThePrimary() {
        assertThat(userService.getById(PRIMARY_ONLY).username()).isEqualTo("primary-only");
    }

    @Test
    @Order(3)
    void write_goesToThePrimaryOnly() {
        UUID id = transactionTemplate.execute(status -> userRepository.saveAndFlush(User.builder()
                .username("written").email("written@example.com").role(Role.SUPERADMIN).active(true)
                .build()).getId());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, id))
                .isEqualTo(1);
        JdbcTemplate replica = new JdbcTemplate(new DriverManagerDataSource(
                REPLICA.getJdbcUrl(), REPLICA.getUsername(), REPLICA.getPassword()));
        assertThat(replica.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    @Order(4)
    void replicationStatus_reportsBothNodes() {
        Map<String, Object> status = replicationStatusService.status();

        assertThat(status).containsEntry("topology", "primary+1-replica");
        assertThat((Map<String, Object>) status.get("primary")).containsEntry("available", true);
        assertThat((Map<String, Object>) status.get("replica-1")).containsEntry("available", true)
                .containsEntry("in_recovery", false);
    }

    @Test
    @Order(5)
    void replicaStopped_eventualReadsFallBackToThePrimary() {
        REPLICA.stop();

        List<String> usernames = userService.getUsers(0, 50).map(UserDto::username).getContent();

        assertThat(usernames).contains("primary-only").doesNotContain("replica-only");
        assertThat((Map<String, Object>) replicationStatusService.status().get("replica-1"))
                .containsEntry("available", false);
    }

    private static void insertUser(JdbcTemplate jdbc, UUID id, String username) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update("INSERT INTO users (id, username, email, role, active, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'SUPERADMIN', true, ?, ?)", id, username, username + "@example.com", now, now);
    }
}
