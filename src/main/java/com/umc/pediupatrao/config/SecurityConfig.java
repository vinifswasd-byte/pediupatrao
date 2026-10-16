package com.umc.pediupatrao.config;

import com.umc.pediupatrao.security.EntradaApiNaoAutenticada;
import com.umc.pediupatrao.security.TratadorAcessoNegado;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Configuração de segurança.
 *
 * A proteção funciona em duas camadas:
 * 1) aqui, pela URL e pelo método HTTP (quem pode chamar cada rota);
 * 2) nos services, com @PreAuthorize (por isso o @EnableMethodSecurity).
 * Esconder botão na tela é só conforto visual; quem barra de verdade é o backend.
 *
 * Resumo de quem pode o quê:
 * ADMIN     - usuários e perfis, consulta de pedidos e auditoria
 * GERENTE   - pedidos (incluindo cancelar e dar desconto), consulta de clientes, produtos e auditoria
 * ATENDENTE - cadastra e altera clientes, registra pedidos e acompanha o andamento
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String GERENTE = "GERENTE";
    private static final String ATENDENTE = "ATENDENTE";

    private final UsuarioDetailsService usuarioDetailsService;
    private final TratadorAcessoNegado tratadorAcessoNegado;
    private final EntradaApiNaoAutenticada entradaApiNaoAutenticada;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService,
            TratadorAcessoNegado tratadorAcessoNegado,
            EntradaApiNaoAutenticada entradaApiNaoAutenticada) {
        this.usuarioDetailsService = usuarioDetailsService;
        this.tratadorAcessoNegado = tratadorAcessoNegado;
        this.entradaApiNaoAutenticada = entradaApiNaoAutenticada;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF agora fica LIGADO. Os formulários Thymeleaf mandam o token sozinhos
                // e o JavaScript das telas manda no cabeçalho (ver meta tags no layout.html).
                // A única exceção são chamadas com usuário e senha explícitos no cabeçalho
                // Authorization (curl, Postman): esse cabeçalho o navegador não envia sozinho
                // a partir de outro site, então não existe o risco que o CSRF previne.
                .csrf(csrf -> csrf.ignoringRequestMatchers(SecurityConfig::temCredencialExplicita))

                // A ordem importa: a primeira regra que combinar com a requisição é a que vale.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/css/**", "/dist/**", "/plugins/**", "/js/**", "/images/**").permitAll()

                        // Usuários e páginas administrativas
                        .requestMatchers("/usuarios/**", "/api/usuarios/**", "/admin", "/teste", "/configuracoes").hasRole(ADMIN)

                        // Auditoria: só leitura, para admin e gerente
                        .requestMatchers("/auditoria/**", "/api/auditoria/**").hasAnyRole(ADMIN, GERENTE)

                        // Cancelamento e desconto: exclusivos do gerente (nem o admin pode)
                        .requestMatchers(HttpMethod.POST,
                                "/pedidos/*/cancelamento", "/api/pedidos/*/cancelamento",
                                "/pedidos/*/descontos", "/api/pedidos/*/descontos").hasRole(GERENTE)

                        // Pedidos: todos consultam, mas o admin não opera
                        .requestMatchers(HttpMethod.GET, "/pedidos/novo").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.GET, "/pedidos", "/api/pedidos", "/api/pedidos/**").hasAnyRole(ADMIN, GERENTE, ATENDENTE)
                        .requestMatchers("/pedidos/**", "/api/pedidos/**").hasAnyRole(GERENTE, ATENDENTE)

                        // Clientes: atendente cadastra e altera, gerente consulta e exclui
                        .requestMatchers(HttpMethod.DELETE, "/api/clientes/**").hasRole(GERENTE)
                        .requestMatchers(HttpMethod.GET, "/clientes", "/api/clientes", "/api/clientes/**").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers("/clientes/**", "/api/clientes/**").hasRole(ATENDENTE)

                        // Produtos: todos consultam, só o gerente mexe no cardápio
                        .requestMatchers(HttpMethod.GET, "/produtos", "/api/produtos", "/api/produtos/**").hasAnyRole(ADMIN, GERENTE, ATENDENTE)
                        .requestMatchers("/produtos/**", "/api/produtos/**").hasRole(GERENTE)

                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(authenticationSuccessHandler())
                )
                // Login por usuário e senha no cabeçalho, para testar a API pelo curl/Postman
                .httpBasic(basic -> basic.authenticationEntryPoint(entradaApiNaoAutenticada))
                .exceptionHandling(erros -> erros
                        .defaultAuthenticationEntryPointFor(entradaApiNaoAutenticada, SecurityConfig::ehRotaDeApi)
                        .accessDeniedHandler(tratadorAcessoNegado)
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.sendRedirect("/"); // Redireciona para a página inicial após login bem-sucedido
        };
    }

    private static boolean temCredencialExplicita(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return authorization != null && authorization.startsWith("Basic ");
    }

    private static boolean ehRotaDeApi(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/");
    }
}
