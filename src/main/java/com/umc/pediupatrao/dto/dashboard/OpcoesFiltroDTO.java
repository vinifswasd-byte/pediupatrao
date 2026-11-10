package com.umc.pediupatrao.dto.dashboard;

import java.time.LocalDate;
import java.util.List;

/** Valores aceitos pelos filtros, para a tela montar os campos sem nada fixo no JavaScript. */
public record OpcoesFiltroDTO(
        LocalDate inicioPadrao,
        LocalDate fimPadrao,
        int maximoDias,
        List<OpcaoDTO> status,
        List<OpcaoDTO> modalidades,
        List<OpcaoDTO> agrupamentos,
        List<OpcaoDTO> produtos) {

    public record OpcaoDTO(String valor, String rotulo) {
    }
}
