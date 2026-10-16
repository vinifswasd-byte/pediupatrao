package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * Repositório da auditoria escrito "na mão" de propósito.
 *
 * Os outros repositórios estendem MongoRepository e por isso ganham de brinde
 * save, delete, deleteAll... Aqui não queremos isso: a auditoria só pode
 * receber registros novos e ser consultada. Como os métodos de alterar e apagar
 * simplesmente não existem, nenhum controller ou service consegue chamá-los.
 */
@Repository
public class AuditoriaRepository {

    private static final String COLECAO = "auditoria";

    private final MongoTemplate mongo;

    public AuditoriaRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** insert (e não save): se o id já existir, o Mongo recusa em vez de sobrescrever. */
    public RegistroAuditoria inserir(RegistroAuditoria registro) {
        return mongo.insert(registro, COLECAO);
    }

    public Optional<RegistroAuditoria> buscarUltimo() {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "sequencia")).limit(1);
        return Optional.ofNullable(mongo.findOne(query, RegistroAuditoria.class, COLECAO));
    }

    /** Consulta do mais recente para o mais antigo, com filtros opcionais. */
    public List<RegistroAuditoria> buscar(String entidade, String entidadeId, int limite) {
        Query query = new Query();
        if (entidade != null && !entidade.isBlank()) {
            query.addCriteria(Criteria.where("entidade").is(entidade));
        }
        if (entidadeId != null && !entidadeId.isBlank()) {
            query.addCriteria(Criteria.where("entidadeId").is(entidadeId.trim()));
        }
        query.with(Sort.by(Sort.Direction.DESC, "sequencia")).limit(limite);
        return mongo.find(query, RegistroAuditoria.class, COLECAO);
    }

    /** Todos os registros na ordem em que foram gravados, usado na verificação da corrente. */
    public List<RegistroAuditoria> buscarTodosEmOrdem() {
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "sequencia"));
        return mongo.find(query, RegistroAuditoria.class, COLECAO);
    }
}
