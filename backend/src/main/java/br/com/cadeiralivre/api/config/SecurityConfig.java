package br.com.cadeiralivre.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import br.com.cadeiralivre.api.security.JwtAuthenticationFilter;
import br.com.cadeiralivre.api.security.JwtService;
import br.com.cadeiralivre.api.security.RespostaErroJson;

/** Regras de acesso por rota. Sessão stateless: quem identifica o usuário é o JWT. */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                // sem login (ou token inválido) em rota protegida
                .authenticationEntryPoint((request, response, ex) ->
                        RespostaErroJson.escrever(response, 401, "Faça login para continuar."))
                // logado, mas sem permissão para a rota (ex.: cliente tentando acessar o painel)
                .accessDeniedHandler((request, response, ex) ->
                        RespostaErroJson.escrever(response, 403, "Você não tem permissão para acessar este recurso.")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**", "/saloes/**", "/error",
                        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/painel/**").hasRole("DONO")
                .anyRequest().authenticated())
            .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Evita a senha aleatória que o Spring Boot gera por padrão.
     * O login de verdade é feito no AuthService e o usuário é identificado pelo JWT,
     * por isso este serviço fica vazio.
     */
    @Bean
    UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
