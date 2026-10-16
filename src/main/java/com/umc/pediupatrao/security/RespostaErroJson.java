package com.umc.pediupatrao.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Monta a resposta de erro da API no formato "Problem Details" (RFC 7807),
 * o mesmo que o ApiErroHandler usa. Assim um 401/403 da segurança tem
 * a mesma cara de um 422 de regra de negócio.
 */
final class RespostaErroJson {

    private RespostaErroJson() {
    }

    static boolean ehChamadaDeApi(HttpServletRequest request) {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        return caminho.startsWith("/api/");
    }

    static void escrever(ObjectMapper json, HttpServletRequest request, HttpServletResponse response,
            int status, String titulo, String detalhe) throws IOException {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("type", "about:blank");
        corpo.put("title", titulo);
        corpo.put("status", status);
        corpo.put("detail", detalhe);
        corpo.put("instance", request.getRequestURI());

        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/problem+json");
        json.writeValue(response.getOutputStream(), corpo);
    }
}
