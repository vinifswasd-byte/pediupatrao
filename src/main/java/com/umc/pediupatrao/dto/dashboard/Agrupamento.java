package com.umc.pediupatrao.dto.dashboard;

/**
 * Como agrupar as vendas no gráfico (RF02).
 * DIA e MES mostram a evolução no tempo; HORA_DO_DIA e DIA_DA_SEMANA
 * mostram os horários e dias de pico, que ajudam a planejar a equipe.
 */
public enum Agrupamento {
    DIA("Por dia"),
    MES("Por mês"),
    HORA_DO_DIA("Por hora do dia"),
    DIA_DA_SEMANA("Por dia da semana");

    private final String descricao;

    Agrupamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
