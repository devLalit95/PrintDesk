package com.example.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.core.convert.converter.Converter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http,
            SecurityProblemWriter problemWriter) throws Exception {
        AuthenticationEntryPoint authenticationEntryPoint = (request, response, authException) ->
                problemWriter.write(
                        request,
                        response,
                        HttpStatus.UNAUTHORIZED,
                        "Authentication required",
                        "Authentication is required to access this resource.",
                        "AUTHENTICATION_REQUIRED");
        AccessDeniedHandler accessDeniedHandler = (request, response, accessDeniedException) ->
                problemWriter.write(
                        request,
                        response,
                        HttpStatus.FORBIDDEN,
                        "Access denied",
                        "You are not authorized to access this resource.",
                        "ACCESS_DENIED");

        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/documents/upload").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/documents/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_AGENT")
                        .requestMatchers(HttpMethod.POST, "/api/print-orders", "/api/print-orders/estimate")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/print-orders/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/admin/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/agents/authenticate").permitAll()
                        .requestMatchers("/ws/agents", "/ws/agents/**").permitAll()
                        .requestMatchers(
                                "/api/admin/agents",
                                "/api/admin/agents/**",
                                "/api/admin/print-jobs/*/resolve-unknown")
                        .hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/admin/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR")
                        .requestMatchers("/api/agents/**").hasAuthority("ROLE_AGENT")
                        .requestMatchers("/api/print-orders/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_OPERATOR")
                        .anyRequest().authenticated())
                .build();
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("authorities");
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return authenticationConverter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
