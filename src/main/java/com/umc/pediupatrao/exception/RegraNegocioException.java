package com.umc.pediupatrao.exception;

/**
 * Usada quando o pedido do usuário até faz sentido, mas quebra uma regra da pizzaria.
 * Exemplo: tentar finalizar um pedido que ainda está no forno.
 */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
