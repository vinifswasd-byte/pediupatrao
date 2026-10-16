package com.umc.pediupatrao.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Transforma as exceções em respostas JSON padronizadas (Problem Details, RFC 7807)
 * para as rotas da API. Assim quem consome a API sempre recebe status + mensagem clara.
 *
 * AccessDeniedException não é tratada aqui de propósito: ela precisa chegar até o
 * Spring Security, que chama o TratadorAcessoNegado (log + auditoria + 403).
 */
@RestControllerAdvice(annotations = RestController.class)
public class ApiErroHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErroHandler.class);

    @ExceptionHandler(RegraNegocioException.class)
    public ProblemDetail regraDeNegocio(RegraNegocioException e) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Regra de negócio", e.getMessage());
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "Não encontrado", e.getMessage());
    }

    @ExceptionHandler(OperacaoNaoPermitidaException.class)
    public ProblemDetail naoPermitido(OperacaoNaoPermitidaException e) {
        return problema(HttpStatus.FORBIDDEN, "Operação não permitida", e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail conflito(OptimisticLockingFailureException e) {
        return problema(HttpStatus.CONFLICT, "Conflito",
                "O pedido foi alterado por outra pessoa ao mesmo tempo. Atualize e tente de novo.");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail requisicaoInvalida(Exception e) {
        log.debug("Requisição inválida", e);
        return problema(HttpStatus.BAD_REQUEST, "Requisição inválida",
                "Confira o formato dos dados enviados (JSON, números e valores permitidos).");
    }

    private ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }
}
