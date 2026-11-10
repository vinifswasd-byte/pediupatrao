package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.dashboard.PedidoAlteradoEvento;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Mantém a conexão aberta com cada dashboard e avisa quando um pedido muda (RF07).
 *
 * Usa Server-Sent Events (SSE): o navegador abre uma conexão que fica ligada,
 * e o servidor manda mensagens por ela quando quiser. É mais simples que
 * WebSocket porque só o servidor fala, que é tudo o que o dashboard precisa.
 *
 * Fluxo: PedidoService salva um pedido -> publica PedidoAlteradoEvento ->
 * este componente recebe (@EventListener) -> envia "pedido-alterado" para
 * todas as telas abertas -> a tela recarrega os números.
 */
@Component
public class PainelTempoReal {

    private static final Logger log = LoggerFactory.getLogger(PainelTempoReal.class);

    /** Depois de 30 minutos a conexão fecha; o navegador reconecta sozinho. */
    static final long TEMPO_CONEXAO_MS = 30L * 60 * 1000;

    private final List<SseEmitter> conexoes = new CopyOnWriteArrayList<>();

    public SseEmitter conectar() {
        SseEmitter conexao = new SseEmitter(TEMPO_CONEXAO_MS);
        conexoes.add(conexao);
        conexao.onCompletion(() -> conexoes.remove(conexao));
        conexao.onTimeout(() -> {
            conexoes.remove(conexao);
            conexao.complete();
        });
        conexao.onError(erro -> conexoes.remove(conexao));

        enviar(conexao, SseEmitter.event().name("conectado").data("ok"));
        log.debug("Dashboard conectado. Conexões abertas: {}", conexoes.size());
        return conexao;
    }

    @EventListener
    public void aoAlterarPedido(PedidoAlteradoEvento evento) {
        for (SseEmitter conexao : conexoes) {
            enviar(conexao, SseEmitter.event().name("pedido-alterado").data(evento));
        }
    }

    /**
     * A cada 25 segundos manda um comentário vazio. Sem isso, alguns proxies
     * e navegadores derrubam conexões paradas, e a tela deixaria de receber avisos.
     */
    @Scheduled(fixedRate = 25_000)
    public void manterConexoesVivas() {
        for (SseEmitter conexao : conexoes) {
            enviar(conexao, SseEmitter.event().comment("ping"));
        }
    }

    public int conexoesAbertas() {
        return conexoes.size();
    }

    // Se a aba foi fechada, o envio falha: só tiramos a conexão da lista.
    private void enviar(SseEmitter conexao, SseEmitter.SseEventBuilder mensagem) {
        try {
            conexao.send(mensagem);
        } catch (IOException | IllegalStateException e) {
            conexoes.remove(conexao);
        }
    }
}
