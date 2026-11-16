package com.bricklayers.projectsservice.config;

import org.springframework.aop.Advisor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/*
  @author   oleksandr
  @project   F2-diploma-project
  @class  SecurityConfig
  @version  1.0.0 
  @since 31.10.2026 - 11.45
*/
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(req -> req
//                                .requestMatchers("/api/v1/roles/superadmin").hasRole("SUPERADMIN")
//                                .requestMatchers("/api/v1/roles/foreman").hasAnyRole("FOREMAN", "SUPERADMIN")
//                                .requestMatchers("/api/v1/roles/builder").hasAnyRole("BUILDER", "SUPERADMIN", "FOREMAN")
//                                .requestMatchers("/api/v1/roles/supplier").hasRole("SUPPLIER")
//                                .requestMatchers("/api/v1/roles/customer").hasRole("CUSTOMER")
                                .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static Advisor preAuthorizeMethodInterceptor() {
        return AuthorizationManagerBeforeMethodInterceptor.preAuthorize();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails admin = User
                .builder()
                .roles("SUPERADMIN")
                .username("admin")
                .password(passwordEncoder().encode("admin"))
                .build();

        UserDetails foreman = User
                .builder()
                .roles("FOREMAN")
                .username(passwordEncoder().encode("foreman"))
                .password("{noop}foreman")
                .build();

        UserDetails builder = User
                .builder()
                .roles("BUILDER")
                .username("builder")
                .password(passwordEncoder().encode("builder"))
                .build();

        UserDetails customer = User
                .builder()
                .roles("CUSTOMER")
                .username("customer")
                .password(passwordEncoder().encode("customer"))
                .build();

        UserDetails supplier = User
                .builder()
                .roles("SUPPLIER")
                .username("supplier")
                .password(passwordEncoder().encode("supplier"))
                .build();

        return new InMemoryUserDetailsManager(admin, foreman, builder, supplier, customer);
    }
}
