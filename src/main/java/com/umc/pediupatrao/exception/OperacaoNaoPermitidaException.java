package com.umc.pediupatrao.exception;

/**
 * O usuário tem acesso à tela, mas aquela ação específica não é permitida para ele.
 * Exemplo: um admin tentando mudar o próprio perfil ou apagar o último admin do sistema.
 * Vira um 403 na API.
 */
public class OperacaoNaoPermitidaException extends RuntimeException {

    public OperacaoNaoPermitidaException(String mensagem) {
        super(mensagem);
    }
}
