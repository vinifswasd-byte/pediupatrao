package com.umc.pediupatrao.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Cancelamento sempre precisa de justificativa. */
public record CancelamentoRequest(String motivo) {

    // Record com um campo só confunde o Jackson às vezes, então deixamos explícito
    // que o JSON esperado é { "motivo": "..." }.
    @JsonCreator
    public CancelamentoRequest(@JsonProperty("motivo") String motivo) {
        this.motivo = motivo;
    }
}
