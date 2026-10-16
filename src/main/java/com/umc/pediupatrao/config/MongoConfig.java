package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.StatusPedido;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

/**
 * Na versão antiga o status era um texto livre ("EM_PREPARO", "ENTREGUE"...).
 * Agora é um enum. Este conversor traduz os nomes antigos na leitura, para que
 * pedidos já gravados continuem aparecendo na lista em vez de dar erro.
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions conversoesPersonalizadas() {
        return new MongoCustomConversions(List.of(new StatusLegadoConverter()));
    }

    @ReadingConverter
    static class StatusLegadoConverter implements Converter<String, StatusPedido> {

        @Override
        public StatusPedido convert(String valor) {
            String normalizado = valor.trim().toUpperCase();
            return switch (normalizado) {
                case "EM_PREPARO" -> StatusPedido.EM_PREPARACAO;
                case "ENTREGUE" -> StatusPedido.FINALIZADO;
                case "SAIU_PARA_ENTREGA", "RETIRADO" -> StatusPedido.DESPACHADO;
                default -> {
                    try {
                        yield StatusPedido.valueOf(normalizado);
                    } catch (IllegalArgumentException e) {
                        yield StatusPedido.RECEBIDO;
                    }
                }
            };
        }
    }
}
