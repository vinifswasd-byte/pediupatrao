package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.config.FormatadorDatas;
import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.CriterioRanking;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.entity.StatusPedido;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas do dashboard feitas direto no MongoDB com aggregation pipeline.
 *
 * Em vez de trazer todos os pedidos para o Java e somar aqui, mandamos o banco
 * filtrar ($match), agrupar ($group) e ordenar ($sort). Só volta o resultado
 * pronto, o que é bem mais rápido quando a pizzaria tiver milhares de pedidos.
 *
 * Os pipelines estão escritos com Document para ficarem iguais ao que se
 * digitaria no shell do Mongo ou no Atlas, o que facilita testar e explicar.
 */
@Repository
public class DashboardRepository {

    private static final String COLECAO = "pedidos";
    private static final String FUSO = FormatadorDatas.FUSO_PIZZARIA.getId();
    private static final String CANCELADO = StatusPedido.CANCELADO.name();

    private final MongoTemplate mongo;

    public DashboardRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** RF01: um único documento com todos os totais do período. */
    public Document resumo(FiltroDashboard filtro) {
        List<Document> pipeline = List.of(
                filtroBasico(filtro),
                new Document("$group", new Document("_id", null)
                        .append("totalPedidos", new Document("$sum", 1))
                        .append("cancelados", somarSe(ehCancelado(), 1, 0))
                        .append("faturamento", somarSe(naoCancelado(), decimal("$valorTotal"), 0))
                        .append("valorCancelado", somarSe(ehCancelado(), decimal("$valorTotal"), 0))
                        .append("descontos", somarSe(naoCancelado(), decimal("$valorDescontos"), 0))
                        .append("itensVendidos", somarSe(naoCancelado(), new Document("$sum", "$itens.quantidade"), 0))
                        .append("clientes", new Document("$addToSet", "$clienteId"))
                        // $avg ignora null, então só entram pedidos que já saíram.
                        // (Arrays.asList porque List.of não aceita null.)
                        .append("tempoMedioSaidaMs", new Document("$avg", new Document("$cond", Arrays.asList(
                                new Document("$gt", Arrays.asList("$saidaEm", null)),
                                new Document("$subtract", List.of("$saidaEm", "$entradaEm")),
                                null))))));
        List<Document> resultado = executar(pipeline);
        return resultado.isEmpty() ? new Document() : resultado.get(0);
    }

    /** RF02: vendas agrupadas por dia, mês, hora ou dia da semana. */
    public List<Document> serie(FiltroDashboard filtro, Agrupamento agrupamento) {
        List<Document> pipeline = List.of(
                filtroBasico(filtro),
                new Document("$group", new Document("_id", chaveDoAgrupamento(agrupamento))
                        .append("pedidos", new Document("$sum", 1))
                        .append("cancelados", somarSe(ehCancelado(), 1, 0))
                        .append("faturamento", somarSe(naoCancelado(), decimal("$valorTotal"), 0))),
                new Document("$sort", new Document("_id", 1)));
        return executar(pipeline);
    }

    /** RF03: quantidade e valor de pedidos em cada status. */
    public List<Document> porStatus(FiltroDashboard filtro) {
        List<Document> pipeline = List.of(
                filtroBasico(filtro),
                new Document("$group", new Document("_id", "$status")
                        .append("quantidade", new Document("$sum", 1))
                        .append("valor", new Document("$sum", decimal("$valorTotal")))));
        return executar(pipeline);
    }

    /**
     * RF04: produtos mais vendidos.
     * O $unwind "abre" a lista de itens: um pedido com 3 itens vira 3 linhas,
     * e aí dá para agrupar por produto. O $facet calcula, na mesma ida ao banco,
     * o ranking e o total geral (usado na % de participação).
     */
    public Document ranking(FiltroDashboard filtro, CriterioRanking criterio, int limite) {
        List<Document> pipeline = new ArrayList<>();
        pipeline.add(filtroBasico(filtro));
        pipeline.add(new Document("$match", new Document("status", new Document("$ne", CANCELADO))));
        pipeline.add(new Document("$unwind", "$itens"));
        if (filtro.produtoId() != null) {
            pipeline.add(new Document("$match", new Document("itens.produtoId", filtro.produtoId())));
        }
        String campoOrdem = criterio == CriterioRanking.FATURAMENTO ? "faturamento" : "quantidade";
        pipeline.add(new Document("$facet", new Document()
                .append("ranking", List.of(
                        new Document("$group", new Document("_id", "$itens.produtoId")
                                .append("nome", new Document("$last", "$itens.nomeProduto"))
                                .append("quantidade", new Document("$sum", "$itens.quantidade"))
                                .append("faturamento", new Document("$sum", decimal("$itens.subtotal")))
                                .append("pedidos", new Document("$sum", 1))),
                        new Document("$sort", new Document(campoOrdem, -1).append("_id", 1)),
                        new Document("$limit", limite)))
                .append("total", List.of(
                        new Document("$group", new Document("_id", null)
                                .append("quantidade", new Document("$sum", "$itens.quantidade"))
                                .append("faturamento", new Document("$sum", decimal("$itens.subtotal"))))))));
        List<Document> resultado = executar(pipeline);
        return resultado.isEmpty() ? new Document() : resultado.get(0);
    }

    /** Pedidos que já saíram, separados por entrega ou retirada. */
    public List<Document> porModalidade(FiltroDashboard filtro) {
        List<Document> pipeline = List.of(
                filtroBasico(filtro),
                new Document("$match", new Document("modalidadeSaida", new Document("$ne", null))
                        .append("status", new Document("$ne", CANCELADO))),
                new Document("$group", new Document("_id", "$modalidadeSaida")
                        .append("pedidos", new Document("$sum", 1))
                        .append("faturamento", new Document("$sum", decimal("$valorTotal")))));
        return executar(pipeline);
    }

    private List<Document> executar(List<Document> pipeline) {
        return mongo.getCollection(COLECAO).aggregate(pipeline).into(new ArrayList<>());
    }

    /**
     * O $match que todas as consultas usam, montado a partir do filtro (RF05).
     * Pedidos da versão antiga do sistema (sem itens) ficam de fora,
     * porque não têm valor nem data de entrada.
     */
    static Document filtroBasico(FiltroDashboard filtro) {
        Document criterios = new Document("entradaEm", new Document("$gte", Date.from(filtro.inicioInstante()))
                .append("$lt", Date.from(filtro.fimExclusivo())))
                .append("itens.0", new Document("$exists", true));
        if (!filtro.status().isEmpty()) {
            criterios.append("status", new Document("$in",
                    filtro.status().stream().map(Enum::name).sorted().toList()));
        }
        if (filtro.modalidade() != null) {
            criterios.append("modalidadeSaida", filtro.modalidade().name());
        }
        if (filtro.produtoId() != null) {
            criterios.append("itens.produtoId", filtro.produtoId());
        }
        return new Document("$match", criterios);
    }

    /** A "etiqueta" de cada grupo no gráfico, sempre no horário de Brasília. */
    static Object chaveDoAgrupamento(Agrupamento agrupamento) {
        Document data = new Document("date", "$entradaEm").append("timezone", FUSO);
        return switch (agrupamento) {
            case DIA -> new Document("$dateToString", new Document(data).append("format", "%Y-%m-%d"));
            case MES -> new Document("$dateToString", new Document(data).append("format", "%Y-%m"));
            case HORA_DO_DIA -> new Document("$hour", data);
            case DIA_DA_SEMANA -> new Document("$isoDayOfWeek", data);
        };
    }

    // Os valores em dinheiro são gravados como texto pelo Spring ("45.00").
    // $toDecimal converte para número decimal antes de somar, sem perder centavos.
    private static Document decimal(String campo) {
        return new Document("$toDecimal", new Document("$ifNull", List.of(campo, "0")));
    }

    private static Document ehCancelado() {
        return new Document("$eq", List.of("$status", CANCELADO));
    }

    private static Document naoCancelado() {
        return new Document("$ne", List.of("$status", CANCELADO));
    }

    private static Document somarSe(Document condicao, Object seVerdadeiro, Object seFalso) {
        return new Document("$sum", new Document("$cond", List.of(condicao, seVerdadeiro, seFalso)));
    }
}
