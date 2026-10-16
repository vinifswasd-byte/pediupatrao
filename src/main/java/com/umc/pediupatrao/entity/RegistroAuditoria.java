package com.umc.pediupatrao.entity;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Um evento da auditoria.
 *
 * É um record de propósito: depois de criado ninguém consegue mudar nada nele
 * pelo código (não existem setters).
 *
 * O "sequencia" numera os eventos (1, 2, 3...) e é único no banco.
 * O "hashAnterior" guarda o hash do evento anterior, formando uma corrente:
 * se alguém mexer num registro direto no banco, a corrente quebra
 * e a verificação de integridade aponta onde foi.
 */
@Document(collection = "auditoria")
public record RegistroAuditoria(
        @Id String id,
        @Indexed(unique = true) long sequencia,
        Instant ocorridoEm,
        String usuario,
        String perfil,
        TipoOperacao operacao,
        String entidade,
        String entidadeId,
        List<AlteracaoCampo> alteracoes,
        String justificativa,
        String hashAnterior,
        String hash) {
}
