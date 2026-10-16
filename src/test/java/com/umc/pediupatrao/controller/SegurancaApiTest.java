package com.umc.pediupatrao.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.security.EntradaApiNaoAutenticada;
import com.umc.pediupatrao.security.TratadorAcessoNegado;
import com.umc.pediupatrao.security.UsuarioLogado;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testa a proteção das rotas da API de pedidos sem subir o banco.
 * O service é falso: o que interessa aqui é se a requisição passa ou é barrada.
 */
@WebMvcTest(PedidoController.class)
@Import({SecurityConfig.class, TratadorAcessoNegado.class, EntradaApiNaoAutenticada.class})
class SegurancaApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PedidoService pedidoService;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @MockitoBean
    private AuditoriaService auditoriaService;

    @MockitoBean
    private UsuarioLogado usuarioLogado;

    private static final String CANCELAMENTO = "{\"motivo\":\"Cliente desistiu\"}";

    @Test
    void semLoginRecebe401() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoCancelaPedido() throws Exception {
        mvc.perform(post("/api/pedidos/abc/cancelamento").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO))
                .andExpect(status().isForbidden());
        verify(pedidoService, never()).cancelar(any(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminTambemNaoCancelaNemDaDesconto() throws Exception {
        mvc.perform(post("/api/pedidos/abc/cancelamento").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos/abc/descontos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"percentual\":10,\"motivo\":\"teste\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteCancela() throws Exception {
        mvc.perform(post("/api/pedidos/abc/cancelamento").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO))
                .andExpect(status().isOk());
        verify(pedidoService).cancelar("abc", "Cliente desistiu");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminConsultaMasNaoRegistraPedido() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isOk());
        mvc.perform(post("/api/pedidos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":\"c1\",\"itens\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void semTokenCsrfOFormularioEhRecusado() throws Exception {
        mvc.perform(post("/api/pedidos/abc/cancelamento")
                        .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO))
                .andExpect(status().isForbidden());
    }
}
