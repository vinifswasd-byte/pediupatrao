package com.umc.pediupatrao.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/**
 * Chamado sempre que alguém logado tenta algo que o perfil dele não permite
 * (ou manda um formulário sem o token CSRF).
 *
 * Três coisas acontecem:
 * 1) fica um aviso no log do servidor;
 * 2) a tentativa vai para a auditoria (ACESSO_NEGADO), então o gerente consegue ver depois;
 * 3) a resposta é um 403: JSON para a API, página de erro para as telas.
 */
@Component
public class TratadorAcessoNegado implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(TratadorAcessoNegado.class);

    private final AuditoriaService auditoria;
    private final UsuarioLogado usuarioLogado;
    private final ObjectMapper json;

    public TratadorAcessoNegado(AuditoriaService auditoria, UsuarioLogado usuarioLogado, ObjectMapper json) {
        this.auditoria = auditoria;
        this.usuarioLogado = usuarioLogado;
        this.json = json;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException excecao) throws IOException {

        String rota = request.getMethod() + " " + request.getRequestURI();
        String motivo = excecao instanceof CsrfException
                ? "Token CSRF ausente ou inválido"
                : "Perfil sem permissão para esta operação";

        log.warn("Acesso negado: usuario={} perfil={} rota={} motivo={}",
                usuarioLogado.nome(), usuarioLogado.perfil(), rota, motivo);

        try {
            auditoria.registrar(TipoOperacao.ACESSO_NEGADO, "Rota", rota, Map.of(), Map.of(), motivo);
        } catch (RuntimeException falha) {
            // Se o banco estiver fora do ar, o bloqueio continua valendo; só registramos no log.
            log.error("Não foi possível gravar a tentativa negada na auditoria", falha);
        }

        if (RespostaErroJson.ehChamadaDeApi(request)) {
            RespostaErroJson.escrever(json, request, response, HttpServletResponse.SC_FORBIDDEN,
                    "Acesso negado", motivo + ".");
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, motivo);
        }
    }
}
