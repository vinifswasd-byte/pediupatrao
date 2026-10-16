package com.umc.pediupatrao.entity;

import com.umc.pediupatrao.exception.RegraNegocioException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * O pedido da pizzaria.
 *
 * Repare que os campos de valor (subtotal, descontos, total) não têm setter.
 * Eles só mudam por dentro, pelos métodos desta classe, sempre recalculados
 * a partir dos itens. Por isso não adianta mandar um "valorTotal" no JSON:
 * o servidor ignora e faz a conta ele mesmo.
 */
@Document(collection = "pedidos")
public class Pedido implements Auditavel {

    @Id
    private String id;

    // Controle de concorrência: se duas pessoas alterarem o mesmo pedido ao mesmo
    // tempo, a segunda recebe erro em vez de apagar sem querer a mudança da primeira.
    @Version
    private Long versao;

    private String clienteId;
    private String clienteNome;

    private List<ItemPedido> itens = new ArrayList<>();
    private List<DescontoAplicado> descontos = new ArrayList<>();

    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal valorDescontos = BigDecimal.ZERO;
    private BigDecimal valorTotal = BigDecimal.ZERO;

    private StatusPedido status;
    private String observacao;

    // Entrada
    private Instant entradaEm;
    private String registradoPor;

    // Saída (entrega ou retirada)
    private Instant saidaEm;
    private String saidaRegistradaPor;
    private ModalidadeSaida modalidadeSaida;

    private Instant finalizadoEm;
    private String finalizadoPor;

    // Cancelamento
    private Instant canceladoEm;
    private String canceladoPor;
    private String motivoCancelamento;

    private List<MovimentacaoPedido> historico = new ArrayList<>();

    // Campos da versão antiga do sistema. Ficam aqui só para os pedidos
    // gravados antes desta evolução continuarem aparecendo na listagem.
    private String tipoPizza;
    private String sabor;
    private int quantidade;

    /**
     * Monta um pedido novo já com status RECEBIDO, horário de entrada e responsável.
     * Esse é o único jeito de criar um pedido "do zero" no sistema.
     */
    public static Pedido novo(Cliente cliente, List<ItemPedido> itens, String observacao,
            String responsavel, Instant agora) {
        if (itens == null || itens.isEmpty()) {
            throw new RegraNegocioException("O pedido precisa de pelo menos um item.");
        }
        Pedido pedido = new Pedido();
        pedido.clienteId = cliente.getId();
        pedido.clienteNome = cliente.getNome();
        pedido.itens = new ArrayList<>(itens);
        pedido.observacao = observacao;
        pedido.status = StatusPedido.RECEBIDO;
        pedido.entradaEm = agora;
        pedido.registradoPor = responsavel;
        pedido.historico.add(new MovimentacaoPedido(null, StatusPedido.RECEBIDO, responsavel, agora, "Pedido registrado"));
        pedido.recalcularValores();
        return pedido;
    }

    /** Muda o status conferindo antes se o caminho é permitido pelo FluxoPedido. */
    public void mudarStatus(StatusPedido novoStatus, String responsavel, Instant agora, String observacao) {
        FluxoPedido.validarTransicao(this.status, novoStatus);
        historico.add(new MovimentacaoPedido(this.status, novoStatus, responsavel, agora, observacao));
        this.status = novoStatus;
    }

    public void registrarSaida(ModalidadeSaida modalidade, String responsavel, Instant agora) {
        if (modalidade == null) {
            throw new RegraNegocioException("Informe se o pedido saiu para entrega ou foi retirado.");
        }
        mudarStatus(StatusPedido.DESPACHADO, responsavel, agora, modalidade.getDescricao());
        this.modalidadeSaida = modalidade;
        this.saidaEm = agora;
        this.saidaRegistradaPor = responsavel;
    }

    public void finalizar(String responsavel, Instant agora) {
        mudarStatus(StatusPedido.FINALIZADO, responsavel, agora, null);
        this.finalizadoEm = agora;
        this.finalizadoPor = responsavel;
    }

    public void cancelar(String motivo, String responsavel, Instant agora) {
        if (!FluxoPedido.podeCancelar(this.status)) {
            throw new RegraNegocioException("Este pedido não pode mais ser cancelado (status atual: "
                    + (status == null ? "-" : status.getDescricao()) + ").");
        }
        mudarStatus(StatusPedido.CANCELADO, responsavel, agora, motivo);
        this.canceladoEm = agora;
        this.canceladoPor = responsavel;
        this.motivoCancelamento = motivo;
        // Itens, valores e descontos ficam como estavam: o histórico é preservado.
    }

    public void adicionarDesconto(DescontoAplicado desconto) {
        if (!FluxoPedido.aceitaDesconto(this.status)) {
            throw new RegraNegocioException("Desconto só pode ser aplicado antes da saída do pedido.");
        }
        descontos.add(desconto);
        recalcularValores();
    }

    /** Soma dos percentuais já concedidos neste pedido. */
    public BigDecimal percentualDescontoAcumulado() {
        return descontos.stream()
                .map(DescontoAplicado::percentual)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void recalcularValores() {
        this.subtotal = itens.stream()
                .map(ItemPedido::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.valorDescontos = descontos.stream()
                .map(DescontoAplicado::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.valorTotal = subtotal.subtract(valorDescontos);
    }

    /** Pedido da versão antiga, sem itens. Só pode ser consultado. */
    public boolean modeloAntigo() {
        return itens == null || itens.isEmpty();
    }

    // Atalhos usados pelos templates para decidir quais botões aparecem.
    // Os nomes não começam com "get" de propósito, para não irem parar no JSON.

    public List<StatusPedido> proximosPassos() {
        return modeloAntigo() ? List.of() : FluxoPedido.proximosPassos(status);
    }

    public boolean aceitaDesconto() {
        return !modeloAntigo() && FluxoPedido.aceitaDesconto(status);
    }

    public boolean podeSerCancelado() {
        return !modeloAntigo() && FluxoPedido.podeCancelar(status);
    }

    public String resumoItens() {
        if (modeloAntigo()) {
            return quantidade + "x " + (tipoPizza == null ? "" : tipoPizza) + " " + (sabor == null ? "" : sabor);
        }
        return itens.stream().map(ItemPedido::resumo).collect(Collectors.joining(", "));
    }

    public String codigoCurto() {
        if (id == null) {
            return "-";
        }
        return id.length() <= 6 ? id : id.substring(id.length() - 6);
    }

    @Override
    public Map<String, String> retratoParaAuditoria() {
        Map<String, String> foto = new LinkedHashMap<>();
        foto.put("cliente", clienteNome);
        foto.put("itens", resumoItensComPreco());
        foto.put("subtotal", texto(subtotal));
        foto.put("percentualDesconto", texto(percentualDescontoAcumulado()));
        foto.put("valorDescontos", texto(valorDescontos));
        foto.put("valorTotal", texto(valorTotal));
        foto.put("status", status == null ? null : status.name());
        foto.put("registradoPor", registradoPor);
        foto.put("modalidadeSaida", modalidadeSaida == null ? null : modalidadeSaida.name());
        foto.put("saidaRegistradaPor", saidaRegistradaPor);
        foto.put("motivoCancelamento", motivoCancelamento);
        // Campos vazios não entram, para a auditoria não ficar cheia de "null -> null".
        foto.values().removeIf(v -> v == null);
        return foto;
    }

    private String resumoItensComPreco() {
        return itens.stream()
                .map(i -> i.quantidade() + "x " + i.nomeProduto() + " @ " + texto(i.precoUnitario()))
                .collect(Collectors.joining("; "));
    }

    private static String texto(BigDecimal valor) {
        return valor == null ? null : valor.toPlainString();
    }

    // Getters (sem setters para os campos calculados)

    public String getId() {
        return id;
    }

    public Long getVersao() {
        return versao;
    }

    public String getClienteId() {
        return clienteId;
    }

    public String getClienteNome() {
        return clienteNome;
    }

    public List<ItemPedido> getItens() {
        return Collections.unmodifiableList(itens);
    }

    public List<DescontoAplicado> getDescontos() {
        return Collections.unmodifiableList(descontos);
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getValorDescontos() {
        return valorDescontos;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public String getObservacao() {
        return observacao;
    }

    public Instant getEntradaEm() {
        return entradaEm;
    }

    public String getRegistradoPor() {
        return registradoPor;
    }

    public Instant getSaidaEm() {
        return saidaEm;
    }

    public String getSaidaRegistradaPor() {
        return saidaRegistradaPor;
    }

    public ModalidadeSaida getModalidadeSaida() {
        return modalidadeSaida;
    }

    public Instant getFinalizadoEm() {
        return finalizadoEm;
    }

    public String getFinalizadoPor() {
        return finalizadoPor;
    }

    public Instant getCanceladoEm() {
        return canceladoEm;
    }

    public String getCanceladoPor() {
        return canceladoPor;
    }

    public String getMotivoCancelamento() {
        return motivoCancelamento;
    }

    public List<MovimentacaoPedido> getHistorico() {
        return Collections.unmodifiableList(historico);
    }

    public String getTipoPizza() {
        return tipoPizza;
    }

    public String getSabor() {
        return sabor;
    }

    public int getQuantidade() {
        return quantidade;
    }
}
