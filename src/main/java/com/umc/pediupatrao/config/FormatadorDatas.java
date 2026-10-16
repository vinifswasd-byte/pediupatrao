package com.umc.pediupatrao.config;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * As datas ficam gravadas em UTC (padrão do Mongo) e aqui convertemos para
 * o horário de Brasília só na hora de mostrar na tela.
 * Nos templates é usado assim: ${@datas.formatar(pedido.entradaEm)}
 */
@Component("datas")
public class FormatadorDatas {

    public static final ZoneId FUSO_PIZZARIA = ZoneId.of("America/Sao_Paulo");

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(FUSO_PIZZARIA);

    public String formatar(Instant instante) {
        return instante == null ? "—" : FORMATO.format(instante);
    }
}
