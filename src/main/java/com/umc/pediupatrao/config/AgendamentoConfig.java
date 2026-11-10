package com.umc.pediupatrao.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga as tarefas agendadas (@Scheduled), usadas para manter o tempo real do dashboard vivo. */
@Configuration
@EnableScheduling
public class AgendamentoConfig {
}
