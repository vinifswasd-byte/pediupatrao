package com.umc.pediupatrao.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.exception.FiltroInvalidoException;
import com.umc.pediupatrao.security.EntradaApiNaoAutenticada;
import com.umc.pediupatrao.security.TratadorAcessoNegado;
import com.umc.pediupatrao.security.UsuarioLogado;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.PainelTempoReal;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Quem pode consultar a API do dashboard e como os erros de filtro respondem. */
@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, TratadorAcessoNegado.class, EntradaApiNaoAutenticada.class})
class DashboardApiSegurancaTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private PainelTempoReal painelTempoReal;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @MockitoBean
    private AuditoriaService auditoriaService;

    @MockitoBean
    private UsuarioLogado usuarioLogado;

    @Test
    void semLoginRecebe401() throws Exception {
        mvc.perform(get("/api/dashboard/resumo")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoVeODashboard() throws Exception {
        mvc.perform(get("/api/dashboard/resumo")).andExpect(status().isForbidden());
        verify(dashboardService, never()).resumo(any());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteConsulta() throws Exception {
        mvc.perform(get("/api/dashboard/resumo?inicio=2026-11-01&fim=2026-11-10")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminConsulta() throws Exception {
        mvc.perform(get("/api/dashboard/produtos/ranking")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void dataEmFormatoErradoDa400() throws Exception {
        mvc.perform(get("/api/dashboard/resumo?inicio=10/11/2026")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void statusInexistenteDa400() throws Exception {
        mvc.perform(get("/api/dashboard/resumo?status=ENTREGUE")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void periodoInvertidoDa400() throws Exception {
        when(dashboardService.montarFiltro(any(), any(), any(), any(), any()))
                .thenThrow(new FiltroInvalidoException("A data inicial é posterior à data final."));
        mvc.perform(get("/api/dashboard/resumo?inicio=2026-11-10&fim=2026-11-01")).andExpect(status().isBadRequest());
    }
}
