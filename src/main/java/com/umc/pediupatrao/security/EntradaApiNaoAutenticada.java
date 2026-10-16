package com.umc.pediupatrao.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Quando alguém chama /api/** sem estar logado, devolvemos 401 em JSON
 * em vez de redirecionar para a tela de login (que não faz sentido para uma API).
 *
 * Não mandamos o cabeçalho WWW-Authenticate de propósito: assim o navegador
 * não abre aquela janelinha nativa pedindo usuário e senha.
 */
@Component
public class EntradaApiNaoAutenticada implements AuthenticationEntryPoint {

    private final ObjectMapper json;

    public EntradaApiNaoAutenticada(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException excecao) throws IOException {
        RespostaErroJson.escrever(json, request, response, HttpServletResponse.SC_UNAUTHORIZED,
                "Não autenticado", "Faça login ou envie usuário e senha (HTTP Basic) para usar a API.");
    }
}
