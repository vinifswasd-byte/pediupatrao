package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.ModalidadeSaida;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.security.UsuarioLogado;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Telas de pedidos. Todas as regras ficam no PedidoService;
 * aqui só recebemos o formulário, chamamos o service e voltamos para a lista.
 * Se o service recusar, o PaginaErroHandler mostra a mensagem na tela.
 */
@Controller
public class PedidoPaginaController {

    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final UsuarioLogado usuarioLogado;

    public PedidoPaginaController(PedidoService pedidoService, ClienteService clienteService,
            ProdutoService produtoService, UsuarioLogado usuarioLogado) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.produtoService = produtoService;
        this.usuarioLogado = usuarioLogado;
    }

    @GetMapping("/pedidos")
    public String pedidos(Model model) {
        model.addAttribute("pedidos", pedidoService.listarPedidos());
        // Só controla quais botões aparecem. Quem bloqueia de verdade é o backend.
        model.addAttribute("podeOperar",
                usuarioLogado.temPerfil(Perfil.GERENTE) || usuarioLogado.temPerfil(Perfil.ATENDENTE));
        model.addAttribute("ehGerente", usuarioLogado.temPerfil(Perfil.GERENTE));
        model.addAttribute("limiteDesconto", PedidoService.LIMITE_DESCONTO_PERCENTUAL);
        model.addAttribute("content", "pedidos :: content");
        return "layout";
    }

    @GetMapping("/pedidos/novo")
    public String novoPedido(Model model) {
        List<Produto> produtos = produtoService.listarDisponiveis();

        // Uma linha por produto, com quantidade 0. O atendente preenche só o que foi pedido.
        PedidoRequest formulario = new PedidoRequest();
        produtos.forEach(p -> formulario.getItens().add(new ItemPedidoRequest(p.getId(), 0)));

        model.addAttribute("formulario", formulario);
        model.addAttribute("produtos", produtos);
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("content", "pedidos/novo :: content");
        return "layout";
    }

    @PostMapping("/pedidos")
    public String registrar(@ModelAttribute PedidoRequest formulario, RedirectAttributes redirect) {
        var pedido = pedidoService.registrar(formulario);
        redirect.addFlashAttribute("sucesso", "Pedido #" + pedido.codigoCurto() + " registrado. Total: R$ "
                + pedido.getValorTotal().toPlainString());
        return "redirect:/pedidos";
    }

    @PostMapping("/pedidos/{id}/avanco")
    public String avancar(@PathVariable String id, @RequestParam StatusPedido destino,
            @RequestParam(required = false) ModalidadeSaida modalidade, RedirectAttributes redirect) {
        var pedido = pedidoService.avancar(id, destino, modalidade);
        redirect.addFlashAttribute("sucesso", "Pedido #" + pedido.codigoCurto() + " agora está: "
                + pedido.getStatus().getDescricao());
        return "redirect:/pedidos";
    }

    @PostMapping("/pedidos/{id}/cancelamento")
    public String cancelar(@PathVariable String id, @RequestParam String motivo, RedirectAttributes redirect) {
        var pedido = pedidoService.cancelar(id, motivo);
        redirect.addFlashAttribute("sucesso", "Pedido #" + pedido.codigoCurto() + " cancelado.");
        return "redirect:/pedidos";
    }

    @PostMapping("/pedidos/{id}/descontos")
    public String aplicarDesconto(@PathVariable String id, @RequestParam BigDecimal percentual,
            @RequestParam String motivo, RedirectAttributes redirect) {
        var pedido = pedidoService.aplicarDesconto(id, percentual, motivo);
        redirect.addFlashAttribute("sucesso", "Desconto aplicado no pedido #" + pedido.codigoCurto()
                + ". Novo total: R$ " + pedido.getValorTotal().toPlainString());
        return "redirect:/pedidos";
    }
}
