package com.umc.pediupatrao.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Em vez de cada classe chamar Instant.now() direto, todas pedem o horário para este Clock.
 * No sistema rodando ele é o relógio de verdade; nos testes trocamos por um relógio parado,
 * e assim dá para conferir datas de entrada e saída sem depender da hora em que o teste roda.
 */
@Configuration
public class RelogioConfig {

    @Bean
    public Clock relogio() {
        return Clock.systemUTC();
    }
}
