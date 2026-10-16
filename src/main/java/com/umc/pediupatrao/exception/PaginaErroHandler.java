package com.umc.pediupatrao.exception;

import com.umc.pediupatrao.controller.AuditoriaPaginaController;
import com.umc.pediupatrao.controller.HomeController;
import com.umc.pediupatrao.controller.PedidoPaginaController;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Para as telas: quando o service recusa alguma coisa, voltamos para a página
 * de onde o usuário veio e mostramos a mensagem num alerta vermelho.
 */
@ControllerAdvice(assignableTypes = {HomeController.class, PedidoPaginaController.class, AuditoriaPaginaController.class})
public class PaginaErroHandler {

    @ExceptionHandler({RegraNegocioException.class, RecursoNaoEncontradoException.class,
        OperacaoNaoPermitidaException.class})
    public String voltarComMensagem(RuntimeException e, HttpServletRequest request) {
        return voltar(request, e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public String conflito(HttpServletRequest request) {
        return voltar(request, "O registro foi alterado por outra pessoa ao mesmo tempo. Confira e tente de novo.");
    }

    private String voltar(HttpServletRequest request, String mensagem) {
        RequestContextUtils.getOutputFlashMap(request).put("erro", mensagem);
        return "redirect:" + paginaAnterior(request);
    }

    // Usa só o caminho do Referer (sem o domínio), para nunca redirecionar para outro site.
    private String paginaAnterior(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null) {
            return "/";
        }
        try {
            URI uri = URI.create(referer);
            String caminho = uri.getRawPath();
            if (caminho == null || !caminho.startsWith("/") || caminho.startsWith("//")) {
                return "/";
            }
            return uri.getRawQuery() == null ? caminho : caminho + "?" + uri.getRawQuery();
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }
}
