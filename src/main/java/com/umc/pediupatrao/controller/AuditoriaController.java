package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.EventoAuditoriaDTO;
import com.umc.pediupatrao.dto.ResultadoVerificacao;
import com.umc.pediupatrao.service.AuditoriaService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de auditoria: só existem rotas GET.
 * Não há POST, PUT nem DELETE aqui, então pela API não dá para criar, editar ou apagar registros.
 */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    /** Ex.: /api/auditoria?entidade=Cliente&entidadeId=123 (os dois filtros são opcionais) */
    @GetMapping
    public List<EventoAuditoriaDTO> consultar(@RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId) {
        return auditoriaService.consultar(entidade, entidadeId);
    }

    @GetMapping("/integridade")
    public ResultadoVerificacao verificarIntegridade() {
        return auditoriaService.verificarIntegridade();
    }
}
