package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.DescontoAplicado;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.ModalidadeSaida;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gera um histórico de pedidos para o dashboard ter o que mostrar.
 *
 * Só roda quando pediupatrao.demo.carga=true no application.properties,
 * e só se o banco tiver poucos pedidos (para não duplicar a carga).
 * Os pedidos são criados pelos mesmos métodos do domínio (Pedido.novo, mudarStatus...),
 * então respeitam o fluxo de status e o cálculo de valores do sistema.
 * Eles ficam marcados com a observação "Pedido de demonstração".
 */
@Configuration
@ConditionalOnProperty(name = "pediupatrao.demo.carga", havingValue = "true")
public class CargaDemonstracao {

    private static final Logger log = LoggerFactory.getLogger(CargaDemonstracao.class);

    static final String OBSERVACAO = "Pedido de demonstração";
    private static final int MINIMO_PARA_PULAR = 50;

    // Peso de cada hora do dia: o movimento se concentra no almoço e, principalmente, à noite
    private static final int[] PESO_POR_HORA = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 4, 3, 1, 1, 1, 2, 5, 9, 10, 8, 4, 1
    };

    @Bean
    CommandLineRunner gerarDadosDeDemonstracao(PedidoRepository pedidos, ProdutoRepository produtos,
            ClienteRepository clientes, Clock relogio, @Value("${pediupatrao.demo.dias:45}") int dias) {
        return args -> {
            long existentes = pedidos.count();
            if (existentes >= MINIMO_PARA_PULAR) {
                log.info("Carga de demonstração ignorada: já existem {} pedidos no banco.", existentes);
                return;
            }
            List<Produto> cardapio = garantirCardapio(produtos);
            List<Cliente> listaClientes = garantirClientes(clientes);
            List<Pedido> gerados = gerar(cardapio, listaClientes, Instant.now(relogio), dias, new Random(2026));
            pedidos.saveAll(gerados);
            log.info("Carga de demonstração: {} pedidos gerados nos últimos {} dias.", gerados.size(), dias);
        };
    }

    /** Monta os pedidos em memória. Separado do banco para poder ser testado. */
    static List<Pedido> gerar(List<Produto> cardapio, List<Cliente> clientes, Instant agora, int dias, Random sorteio) {
        LocalDate hoje = agora.atZone(FormatadorDatas.FUSO_PIZZARIA).toLocalDate();
        List<Pedido> gerados = new ArrayList<>();

        for (int atras = dias - 1; atras >= 0; atras--) {
            LocalDate dia = hoje.minusDays(atras);
            boolean fimDeSemana = dia.getDayOfWeek().getValue() >= DayOfWeek.FRIDAY.getValue();
            // Um leve crescimento ao longo do período, para o comparativo ter graça
            int quantidade = (fimDeSemana ? 8 : 4) + sorteio.nextInt(fimDeSemana ? 7 : 5) + (dias - atras) / 15;

            for (int i = 0; i < quantidade; i++) {
                Instant entrada = dia.atTime(sortearHora(sorteio), sorteio.nextInt(60))
                        .atZone(FormatadorDatas.FUSO_PIZZARIA).toInstant();
                if (entrada.isAfter(agora)) {
                    continue; // não cria pedido no futuro
                }
                gerados.add(montarPedido(cardapio, clientes, entrada, agora, sorteio));
            }
        }
        return gerados;
    }

    private static Pedido montarPedido(List<Produto> cardapio, List<Cliente> clientes, Instant entrada,
            Instant agora, Random sorteio) {
        Cliente cliente = clientes.get(sorteio.nextInt(clientes.size()));
        Pedido pedido = Pedido.novo(cliente, sortearItens(cardapio, sorteio), OBSERVACAO, "atendente01", entrada);

        if (sorteio.nextInt(100) < 10) {
            BigDecimal percentual = BigDecimal.valueOf(5L * (1 + sorteio.nextInt(3)));
            BigDecimal valor = pedido.getSubtotal().multiply(percentual).divide(new BigDecimal("100"), 2, RoundingMode.HALF_EVEN);
            pedido.adicionarDesconto(new DescontoAplicado(percentual, valor, "Cortesia", "gerente01",
                    entrada.plus(Duration.ofMinutes(2))));
        }

        Instant cancelamento = entrada.plus(Duration.ofMinutes(8));
        if (sorteio.nextInt(100) < 8 && !cancelamento.isAfter(agora)) {
            pedido.cancelar("Cliente desistiu", "gerente01", cancelamento);
            return pedido;
        }

        // Avança cada etapa só se o horário dela já passou: pedidos recentes ficam abertos
        Instant preparo = entrada.plus(Duration.ofMinutes(3 + sorteio.nextInt(6)));
        Instant pronto = preparo.plus(Duration.ofMinutes(15 + sorteio.nextInt(16)));
        Instant saida = pronto.plus(Duration.ofMinutes(3 + sorteio.nextInt(8)));
        Instant fim = saida.plus(Duration.ofMinutes(15 + sorteio.nextInt(21)));

        if (!preparo.isAfter(agora)) {
            pedido.mudarStatus(StatusPedido.EM_PREPARACAO, "atendente01", preparo, null);
        }
        if (!pronto.isAfter(agora)) {
            pedido.mudarStatus(StatusPedido.PRONTO, "atendente01", pronto, null);
        }
        if (!saida.isAfter(agora)) {
            ModalidadeSaida modalidade = sorteio.nextInt(100) < 70 ? ModalidadeSaida.ENTREGA : ModalidadeSaida.RETIRADA;
            pedido.registrarSaida(modalidade, "atendente01", saida);
        }
        if (!fim.isAfter(agora)) {
            pedido.finalizar("atendente01", fim);
        }
        return pedido;
    }

    private static List<ItemPedido> sortearItens(List<Produto> cardapio, Random sorteio) {
        List<ItemPedido> itens = new ArrayList<>();
        int quantosProdutos = 1 + sorteio.nextInt(Math.min(3, cardapio.size()));
        List<Produto> disponiveis = new ArrayList<>(cardapio);
        for (int i = 0; i < quantosProdutos; i++) {
            // Os primeiros do cardápio saem mais (deixa o ranking interessante)
            int indice = Math.min(sorteio.nextInt(disponiveis.size()), sorteio.nextInt(disponiveis.size()));
            Produto produto = disponiveis.remove(indice);
            itens.add(ItemPedido.de(produto, 1 + sorteio.nextInt(2)));
        }
        return itens;
    }

    private static int sortearHora(Random sorteio) {
        int total = 0;
        for (int peso : PESO_POR_HORA) {
            total += peso;
        }
        int sorteado = sorteio.nextInt(total);
        for (int hora = 0; hora < PESO_POR_HORA.length; hora++) {
            sorteado -= PESO_POR_HORA[hora];
            if (sorteado < 0) {
                return hora;
            }
        }
        return 20;
    }

    private static List<Produto> garantirCardapio(ProdutoRepository produtos) {
        List<Produto> ativos = produtos.findAll().stream()
                .filter(Produto::disponivelParaVenda)
                .filter(p -> p.getPreco() != null && p.getPreco() > 0)
                .toList();
        if (ativos.size() >= 4) {
            return ativos;
        }
        Object[][] cardapio = {
            {"Pizza Calabresa", "Pizza", 45.0}, {"Pizza Frango c/ Catupiry", "Pizza", 47.0},
            {"Pizza Margherita", "Pizza", 42.0}, {"Pizza Portuguesa", "Pizza", 48.0},
            {"Refrigerante 2L", "Bebida", 12.0}, {"Brownie", "Sobremesa", 15.0}
        };
        List<Produto> novos = new ArrayList<>(ativos);
        for (Object[] linha : cardapio) {
            Produto p = new Produto();
            p.setNome((String) linha[0]);
            p.setTipo((String) linha[1]);
            p.setPreco((Double) linha[2]);
            p.setAtivo(Boolean.TRUE);
            novos.add(produtos.save(p));
        }
        return novos;
    }

    private static List<Cliente> garantirClientes(ClienteRepository clientes) {
        List<Cliente> existentes = clientes.findAll();
        if (existentes.size() >= 6) {
            return existentes;
        }
        String[] nomes = {"Ana Ribeiro", "Bruno Costa", "Carla Mendes", "Diego Alves",
            "Elisa Martins", "Felipe Rocha", "Gabriela Nunes", "Henrique Dias"};
        List<Cliente> todos = new ArrayList<>(existentes);
        for (int i = 0; i < nomes.length; i++) {
            Cliente c = new Cliente();
            c.setNome(nomes[i]);
            c.setTelefone("1190000000" + i);
            c.setLogradouro("Rua de Teste");
            c.setNumero(String.valueOf(100 + i));
            c.setCidade("Mogi das Cruzes");
            c.setEstado("SP");
            todos.add(clientes.save(c));
        }
        return todos;
    }
}
