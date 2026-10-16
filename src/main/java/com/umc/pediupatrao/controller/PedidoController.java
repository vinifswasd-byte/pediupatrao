package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.CancelamentoRequest;
import com.umc.pediupatrao.dto.DescontoRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.ModalidadeSaida;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.service.PedidoService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API de pedidos.
 *
 * Em vez de um único "mude o status para X", cada etapa tem sua própria rota.
 * Assim fica claro no código (e na configuração de segurança) quem pode fazer o quê,
 * e o cancelamento/desconto ficam em rotas que só o gerente alcança.
 */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping
    public List<Pedido> listarPedidos() {
        return pedidoService.listarPedidos();
    }

    @GetMapping("/{id}")
    public Pedido buscar(@PathVariable String id) {
        return pedidoService.buscar(id);
    }

    @PostMapping
    public ResponseEntity<Pedido> criarPedido(@RequestBody PedidoRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.registrar(dados));
    }

    @PostMapping("/{id}/preparo")
    public Pedido enviarParaCozinha(@PathVariable String id) {
        return pedidoService.avancar(id, StatusPedido.EM_PREPARACAO, null);
    }

    @PostMapping("/{id}/pronto")
    public Pedido marcarComoPronto(@PathVariable String id) {
        return pedidoService.avancar(id, StatusPedido.PRONTO, null);
    }

    /** Saída do pedido: ?modalidade=ENTREGA ou ?modalidade=RETIRADA */
    @PostMapping("/{id}/saida")
    public Pedido registrarSaida(@PathVariable String id, @RequestParam ModalidadeSaida modalidade) {
        return pedidoService.avancar(id, StatusPedido.DESPACHADO, modalidade);
    }

    @PostMapping("/{id}/finalizacao")
    public Pedido finalizar(@PathVariable String id) {
        return pedidoService.avancar(id, StatusPedido.FINALIZADO, null);
    }

    @PostMapping("/{id}/cancelamento")
    public Pedido cancelar(@PathVariable String id, @RequestBody CancelamentoRequest dados) {
        return pedidoService.cancelar(id, dados.motivo());
    }

    @PostMapping("/{id}/descontos")
    public Pedido aplicarDesconto(@PathVariable String id, @RequestBody DescontoRequest dados) {
        return pedidoService.aplicarDesconto(id, dados.percentual(), dados.motivo());
    }

    /**
     * Rota da versão anterior, mantida para não quebrar quem já usava.
     * Agora ela passa pelas mesmas validações do fluxo.
     */
    @PutMapping("/{id}/status")
    public Pedido atualizarStatus(@PathVariable String id, @RequestParam StatusPedido status,
            @RequestParam(required = false) ModalidadeSaida modalidade) {
        return pedidoService.avancar(id, status, modalidade);
    }
}
