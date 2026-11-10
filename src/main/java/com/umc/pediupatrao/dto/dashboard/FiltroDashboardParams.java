package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.ModalidadeSaida;
import com.umc.pediupatrao.entity.StatusPedido;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Parâmetros de filtro do jeito que chegam na URL, por exemplo:
 * /api/dashboard/resumo?inicio=2026-11-01&fim=2026-11-10&status=FINALIZADO&status=CANCELADO
 *
 * Ainda não estão validados: o DashboardService transforma isto em FiltroDashboard,
 * aplicando os valores padrão e recusando combinações inválidas.
 */
public class FiltroDashboardParams {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate inicio;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fim;

    private List<StatusPedido> status = new ArrayList<>();
    private ModalidadeSaida modalidade;
    private String produtoId;

    public LocalDate getInicio() {
        return inicio;
    }

    public void setInicio(LocalDate inicio) {
        this.inicio = inicio;
    }

    public LocalDate getFim() {
        return fim;
    }

    public void setFim(LocalDate fim) {
        this.fim = fim;
    }

    public List<StatusPedido> getStatus() {
        return status;
    }

    public void setStatus(List<StatusPedido> status) {
        this.status = status;
    }

    public ModalidadeSaida getModalidade() {
        return modalidade;
    }

    public void setModalidade(ModalidadeSaida modalidade) {
        this.modalidade = modalidade;
    }

    public String getProdutoId() {
        return produtoId;
    }

    public void setProdutoId(String produtoId) {
        this.produtoId = produtoId;
    }
}
