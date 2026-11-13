package com.villanos.grafo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        // Modificaciones a la red (crear paradas, cambiar tramos): SOLO planificador o admin
                        .requestMatchers(HttpMethod.POST, "/api/redes/**").hasAnyRole("planificador", "admin")
                        .requestMatchers(HttpMethod.PUT, "/api/redes/**").hasAnyRole("planificador", "admin")
                        .requestMatchers(HttpMethod.DELETE, "/api/redes/**").hasAnyRole("planificador", "admin")
                        .requestMatchers(HttpMethod.PATCH, "/api/redes/**").hasAnyRole("planificador", "admin")
                        // Consultas de la red: consultor, planificador o admin
                        .requestMatchers(HttpMethod.GET, "/api/redes/**").hasAnyRole("consultor", "planificador", "admin")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtAuthenticationConverter()))
                );

        return http.build();
    }
}
