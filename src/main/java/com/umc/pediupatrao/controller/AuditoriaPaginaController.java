package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.ResultadoVerificacao;
import com.umc.pediupatrao.service.AuditoriaService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Tela de consulta da auditoria (somente leitura). */
@Controller
public class AuditoriaPaginaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaPaginaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/auditoria")
    public String auditoria(@RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId, Model model) {
        model.addAttribute("eventos", auditoriaService.consultar(entidade, entidadeId));
        model.addAttribute("entidades", List.of("Pedido", "Cliente", "Produto", "Usuario", "Rota"));
        model.addAttribute("entidade", entidade);
        model.addAttribute("entidadeId", entidadeId);
        model.addAttribute("content", "auditoria :: content");
        return "layout";
    }

    @GetMapping("/auditoria/integridade")
    public String verificar(RedirectAttributes redirect) {
        ResultadoVerificacao resultado = auditoriaService.verificarIntegridade();
        redirect.addFlashAttribute(resultado.integra() ? "sucesso" : "erro", resultado.mensagem());
        return "redirect:/auditoria";
    }
}
