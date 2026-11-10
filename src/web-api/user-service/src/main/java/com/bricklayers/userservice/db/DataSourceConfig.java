package com.bricklayers.userservice.db;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

@Configuration
public class DataSourceConfig {

    @Bean
    @LiquibaseDataSource
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource primaryDataSource(DataSourceProperties properties) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        dataSource.setPoolName("primary");
        return dataSource;
    }

    @Bean
    @ConditionalOnProperty(name = "app.datasource.replica.enabled", havingValue = "true")
    public ReplicaPool replicaPool(@Value("${app.datasource.replica.url}") String urls,
                                   @Value("${app.datasource.replica.username}") String username,
                                   @Value("${app.datasource.replica.password}") String password,
                                   @Value("${app.datasource.replica.pool-size:10}") int poolSize,
                                   @Value("${app.datasource.replica.application-name:user-service-replica}") String applicationName) {
        List<ReplicaPool.Replica> replicas = new ArrayList<>();
        List<String> jdbcUrls = Arrays.stream(urls.split(",")).map(String::trim).filter(url -> !url.isEmpty()).toList();
        for (int index = 0; index < jdbcUrls.size(); index++) {
            String name = "replica-" + (index + 1);
            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setPoolName(name);
            dataSource.setJdbcUrl(jdbcUrls.get(index));
            dataSource.setUsername(username);
            dataSource.setPassword(password);
            dataSource.setReadOnly(true);
            dataSource.setMaximumPoolSize(poolSize);
            dataSource.setConnectionTimeout(1000);
            dataSource.setInitializationFailTimeout(-1);
            Properties properties = new Properties();
            properties.setProperty("ApplicationName", applicationName);
            dataSource.setDataSourceProperties(properties);
            replicas.add(new ReplicaPool.Replica(name, dataSource,
                    NodeHealth.probing(name, jdbcUrls.get(index), username, password)));
        }
        return new ReplicaPool(replicas);
    }

    @Bean
    public NodeHealth primaryHealth(DataSourceProperties properties) {
        return NodeHealth.probing("primary", properties.determineUrl(),
                properties.determineUsername(), properties.determinePassword());
    }

    @Bean
    public ReadYourWritesTracker readYourWritesTracker(
            ObjectProvider<StringRedisTemplate> redis,
            @Value("${app.datasource.replica.enabled:false}") boolean replicaEnabled,
            @Value("${app.datasource.read-your-writes-ttl:5s}") Duration ttl) {
        return new ReadYourWritesTracker(redis.getIfAvailable(), replicaEnabled ? ttl : Duration.ZERO);
    }

    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("primaryDataSource") HikariDataSource primary,
                                 NodeHealth primaryHealth,
                                 ObjectProvider<ReplicaPool> replicaPool,
                                 ReadYourWritesTracker tracker) {
        ReadWriteRoutingDataSource routing = new ReadWriteRoutingDataSource(
                primary, primaryHealth, replicaPool.getIfAvailable(), tracker);
        LazyConnectionDataSourceProxy lazy = new LazyConnectionDataSourceProxy(routing);
        lazy.setDefaultAutoCommit(true);
        lazy.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        return lazy;
    }

    @Bean
    public WebMvcConfigurer readYourWritesWebConfigurer(ReadYourWritesTracker tracker) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new ReadYourWritesInterceptor(tracker)).addPathPatterns("/api/**");
            }
        };
    }

    @Configuration
    static class NodeProbeConfig {

        private final NodeHealth primaryHealth;
        private final ReplicaPool replicaPool;

        NodeProbeConfig(NodeHealth primaryHealth, ObjectProvider<ReplicaPool> replicaPool) {
            this.primaryHealth = primaryHealth;
            this.replicaPool = replicaPool.getIfAvailable();
        }

        @Scheduled(initialDelayString = "${app.datasource.replica.probe-interval-ms:2000}",
                fixedDelayString = "${app.datasource.replica.probe-interval-ms:2000}")
        void probeNodes() {
            primaryHealth.probe();
            if (replicaPool != null) {
                replicaPool.probeAll();
            }
        }
    }
}
