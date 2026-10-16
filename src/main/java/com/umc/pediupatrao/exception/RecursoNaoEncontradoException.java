package com.umc.pediupatrao.exception;

/**
 * O id informado não existe no banco (cliente, pedido, produto ou usuário).
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String oQue, String id) {
        super(oQue + " não encontrado(a): " + id);
    }
}
