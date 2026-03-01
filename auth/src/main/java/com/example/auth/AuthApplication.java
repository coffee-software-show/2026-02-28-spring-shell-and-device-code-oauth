package com.example.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootApplication
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }


    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .oauth2AuthorizationServer(a -> {
                    a.oidc(Customizer.withDefaults());
                    a.deviceAuthorizationEndpoint(Customizer.withDefaults());
                    a.deviceVerificationEndpoint(Customizer.withDefaults());
                })
                .formLogin(Customizer.withDefaults())
                .build();
    }
}
