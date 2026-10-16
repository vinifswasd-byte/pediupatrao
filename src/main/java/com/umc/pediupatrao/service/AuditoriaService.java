package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.EventoAuditoriaDTO;
import com.umc.pediupatrao.dto.ResultadoVerificacao;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Cuida de tudo que é auditoria: gravar, consultar e conferir se ninguém mexeu.
 *
 * Os outros services chamam registrar(...) logo depois de salvar alguma coisa,
 * passando a "foto" de antes e a de depois. Aqui a gente descobre o que mudou,
 * carimba usuário, perfil e horário, e encadeia o hash com o registro anterior.
 */
@Service
public class AuditoriaService {

    /** Hash "zero" usado pelo primeiro registro, que não tem ninguém antes dele. */
    public static final String HASH_INICIAL = "0".repeat(64);

    private static final int LIMITE_CONSULTA = 200;

    private final AuditoriaRepository repositorio;
    private final UsuarioLogado usuarioLogado;
    private final Clock relogio;

    public AuditoriaService(AuditoriaRepository repositorio, UsuarioLogado usuarioLogado, Clock relogio) {
        this.repositorio = repositorio;
        this.usuarioLogado = usuarioLogado;
        this.relogio = relogio;
    }

    /**
     * Grava um evento na auditoria.
     *
     * Se for uma alteração em que nada mudou de verdade (o usuário só clicou em salvar),
     * não grava nada e devolve null, para não poluir a consulta.
     *
     * O synchronized garante que dois eventos ao mesmo tempo não peguem o mesmo
     * "registro anterior" e quebrem a corrente. Além disso, a sequência é única no banco.
     */
    public synchronized RegistroAuditoria registrar(TipoOperacao operacao, String entidade, String entidadeId,
            Map<String, String> antes, Map<String, String> depois, String justificativa) {

        List<AlteracaoCampo> alteracoes = compararRetratos(antes, depois);
        boolean eraUmaEdicao = !antes.isEmpty() && !depois.isEmpty();
        if (eraUmaEdicao && alteracoes.isEmpty()) {
            return null;
        }

        RegistroAuditoria anterior = repositorio.buscarUltimo().orElse(null);
        long sequencia = anterior == null ? 1 : anterior.sequencia() + 1;
        String hashAnterior = anterior == null ? HASH_INICIAL : anterior.hash();

        // O Mongo guarda datas com precisão de milissegundos. Cortamos aqui para
        // o hash calculado agora bater com o que será lido do banco depois.
        Instant agora = Instant.now(relogio).truncatedTo(ChronoUnit.MILLIS);

        RegistroAuditoria semHash = new RegistroAuditoria(
                UUID.randomUUID().toString(), sequencia, agora,
                usuarioLogado.nome(), usuarioLogado.perfil(),
                operacao, entidade, entidadeId, alteracoes, justificativa,
                hashAnterior, null);

        RegistroAuditoria completo = new RegistroAuditoria(
                semHash.id(), semHash.sequencia(), semHash.ocorridoEm(),
                semHash.usuario(), semHash.perfil(), semHash.operacao(),
                semHash.entidade(), semHash.entidadeId(), semHash.alteracoes(),
                semHash.justificativa(), semHash.hashAnterior(), calcularHash(semHash));

        return repositorio.inserir(completo);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<EventoAuditoriaDTO> consultar(String entidade, String entidadeId) {
        return repositorio.buscar(entidade, entidadeId, LIMITE_CONSULTA).stream()
                .map(this::paraExibicao)
                .toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ResultadoVerificacao verificarIntegridade() {
        return verificarCorrente(repositorio.buscarTodosEmOrdem());
    }

    /**
     * Compara duas fotos e devolve só o que mudou.
     * Campos que só existem em uma das fotos também entram (ex.: na inclusão, tudo é novo).
     */
    public static List<AlteracaoCampo> compararRetratos(Map<String, String> antes, Map<String, String> depois) {
        Set<String> campos = new LinkedHashSet<>();
        campos.addAll(antes.keySet());
        campos.addAll(depois.keySet());

        List<AlteracaoCampo> mudancas = new ArrayList<>();
        for (String campo : campos) {
            String valorAntes = antes.get(campo);
            String valorDepois = depois.get(campo);
            if (!Objects.equals(valorAntes, valorDepois)) {
                mudancas.add(new AlteracaoCampo(campo, valorAntes, valorDepois));
            }
        }
        return mudancas;
    }

    /**
     * Percorre todos os registros em ordem e confere duas coisas em cada um:
     * 1) se o hashAnterior aponta mesmo para o registro de trás;
     * 2) se o hash gravado bate com o conteúdo atual (ou seja, ninguém editou).
     */
    public static ResultadoVerificacao verificarCorrente(List<RegistroAuditoria> registros) {
        String hashEsperadoAnterior = HASH_INICIAL;
        long sequenciaEsperada = 1;

        for (RegistroAuditoria registro : registros) {
            if (registro.sequencia() != sequenciaEsperada) {
                return new ResultadoVerificacao(false, registros.size(), registro.sequencia(),
                        "Sequência fora de ordem: esperado " + sequenciaEsperada
                        + ", encontrado " + registro.sequencia() + ". Algum registro pode ter sido apagado.");
            }
            if (!hashEsperadoAnterior.equals(registro.hashAnterior())) {
                return new ResultadoVerificacao(false, registros.size(), registro.sequencia(),
                        "O registro " + registro.sequencia() + " não aponta para o registro anterior.");
            }
            if (!calcularHash(registro).equals(registro.hash())) {
                return new ResultadoVerificacao(false, registros.size(), registro.sequencia(),
                        "O conteúdo do registro " + registro.sequencia() + " foi alterado depois de gravado.");
            }
            hashEsperadoAnterior = registro.hash();
            sequenciaEsperada++;
        }
        return new ResultadoVerificacao(true, registros.size(), null,
                "Corrente íntegra: " + registros.size() + " registro(s) conferido(s).");
    }

    /**
     * SHA-256 de todos os campos do registro, menos o próprio hash.
     * Cada valor entra com o tamanho na frente ("5:Pedido;"), assim não dá para
     * mover texto de um campo para o outro e chegar no mesmo resultado.
     */
    static String calcularHash(RegistroAuditoria r) {
        StringBuilder conteudo = new StringBuilder();
        adicionar(conteudo, r.id());
        adicionar(conteudo, String.valueOf(r.sequencia()));
        adicionar(conteudo, r.ocorridoEm() == null ? null : String.valueOf(r.ocorridoEm().toEpochMilli()));
        adicionar(conteudo, r.usuario());
        adicionar(conteudo, r.perfil());
        adicionar(conteudo, r.operacao() == null ? null : r.operacao().name());
        adicionar(conteudo, r.entidade());
        adicionar(conteudo, r.entidadeId());
        if (r.alteracoes() != null) {
            for (AlteracaoCampo a : r.alteracoes()) {
                adicionar(conteudo, a.campo());
                adicionar(conteudo, a.valorAnterior());
                adicionar(conteudo, a.valorNovo());
            }
        }
        adicionar(conteudo, r.justificativa());
        adicionar(conteudo, r.hashAnterior());
        return sha256(conteudo.toString());
    }

    private static void adicionar(StringBuilder sb, String valor) {
        String v = valor == null ? "" : valor;
        sb.append(v.length()).append(':').append(v).append(';');
    }

    private static String sha256(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Todo Java vem com SHA-256, então isso na prática nunca acontece.
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /**
     * Prepara o registro para aparecer na tela.
     * Telefone é dado pessoal: na consulta mostramos só o DDD e os 4 últimos dígitos,
     * o que já basta para saber que o número mudou sem expor o número inteiro.
     */
    private EventoAuditoriaDTO paraExibicao(RegistroAuditoria r) {
        List<AlteracaoCampo> alteracoes = r.alteracoes() == null ? List.of() : r.alteracoes().stream()
                .map(a -> "telefone".equals(a.campo())
                        ? new AlteracaoCampo(a.campo(), mascararTelefone(a.valorAnterior()), mascararTelefone(a.valorNovo()))
                        : a)
                .toList();

        return new EventoAuditoriaDTO(r.sequencia(), r.ocorridoEm(), r.usuario(), r.perfil(),
                r.operacao(), r.operacao() == null ? "" : r.operacao().getDescricao(),
                r.entidade(), r.entidadeId(), alteracoes, r.justificativa(),
                r.hash() == null ? "" : r.hash().substring(0, Math.min(12, r.hash().length())));
    }

    public static String mascararTelefone(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return telefone;
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() < 6) {
            return "****";
        }
        String ddd = digitos.substring(0, 2);
        String finais = digitos.substring(digitos.length() - 4);
        return "(" + ddd + ") *****-" + finais;
    }
}
