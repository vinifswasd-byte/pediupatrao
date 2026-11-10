// Dashboard administrativo do Pediu Patrão.
// Esta tela não recebe dados prontos do servidor: tudo vem da API /api/dashboard (RF06).
// Organização: filtros -> busca na API -> desenha indicadores, gráficos e fila.
// Um canal de tempo real (SSE) avisa quando um pedido muda e a tela se atualiza sozinha (RF07).

(function () {
    'use strict';

    const API = '/api/dashboard';
    const ESPERA_ANTES_DE_RECARREGAR_MS = 1500; // junta vários avisos seguidos numa recarga só
    const INTERVALO_RESERVA_MS = 60000;        // se o tempo real cair, atualiza a cada 1 minuto

    const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
    const numero = new Intl.NumberFormat('pt-BR');
    const hora = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    const dataHora = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

    // Cores por status, as mesmas ideias dos badges da tela de pedidos
    const CORES_STATUS = {
        RECEBIDO: '#6c757d', EM_PREPARACAO: '#ffc107', PRONTO: '#0dcaf0',
        DESPACHADO: '#0d6efd', FINALIZADO: '#198754', CANCELADO: '#dc3545'
    };

    const el = (id) => document.getElementById(id);
    const graficos = {};
    let opcoes = null;
    let timerRecarga = null;
    let timerReserva = null;
    let carregando = false;

    document.addEventListener('DOMContentLoaded', iniciar);

    async function iniciar() {
        if (typeof echarts === 'undefined') {
            mostrarAlerta('danger', 'Não foi possível carregar a biblioteca de gráficos (sem internet?). Os números continuam disponíveis na API.');
        } else {
            ['graficoVendas', 'graficoStatus', 'graficoRanking', 'graficoHoras', 'graficoCanais']
                .forEach((id) => { graficos[id] = echarts.init(el(id)); });
            window.addEventListener('resize', () => Object.values(graficos).forEach((g) => g.resize()));
        }

        try {
            opcoes = await buscar(`${API}/opcoes`);
        } catch (erro) {
            mostrarAlerta('danger', 'Não foi possível carregar o dashboard: ' + erro.message);
            return;
        }
        montarCamposDeFiltro();
        lerFiltrosDaUrl();
        ligarEventos();
        await carregarTudo();
        conectarTempoReal();
    }

    // Filtros (RF05)

    function montarCamposDeFiltro() {
        const lista = el('listaStatus');
        opcoes.status.forEach((s) => {
            const linha = document.createElement('div');
            linha.className = 'form-check';
            const caixa = document.createElement('input');
            caixa.type = 'checkbox';
            caixa.className = 'form-check-input';
            caixa.id = 'st_' + s.valor;
            caixa.value = s.valor;
            caixa.addEventListener('change', atualizarRotuloStatus);
            const rotulo = document.createElement('label');
            rotulo.className = 'form-check-label';
            rotulo.htmlFor = caixa.id;
            rotulo.textContent = s.rotulo;
            linha.append(caixa, rotulo);
            lista.append(linha);
        });
        preencherSelect(el('fModalidade'), opcoes.modalidades);
        preencherSelect(el('fProduto'), opcoes.produtos);
        preencherSelect(el('fAgrupamento'), opcoes.agrupamentos, false);
        el('fInicio').value = opcoes.inicioPadrao;
        el('fFim').value = opcoes.fimPadrao;
    }

    function preencherSelect(select, itens, manterPrimeira = true) {
        if (!manterPrimeira) {
            select.innerHTML = '';
        }
        itens.forEach((item) => select.add(new Option(item.rotulo, item.valor)));
    }

    function statusMarcados() {
        return [...document.querySelectorAll('#listaStatus input:checked')].map((c) => c.value);
    }

    function atualizarRotuloStatus() {
        const marcados = statusMarcados();
        el('btnStatus').textContent = marcados.length === 0 ? 'Todos'
            : marcados.length === 1 ? opcoes.status.find((s) => s.valor === marcados[0]).rotulo
            : marcados.length + ' selecionados';
    }

    // Monta a query string que vai para TODAS as chamadas da API
    function parametrosDoFiltro() {
        const p = new URLSearchParams();
        if (el('fInicio').value) p.set('inicio', el('fInicio').value);
        if (el('fFim').value) p.set('fim', el('fFim').value);
        statusMarcados().forEach((s) => p.append('status', s));
        if (el('fModalidade').value) p.set('modalidade', el('fModalidade').value);
        if (el('fProduto').value) p.set('produtoId', el('fProduto').value);
        return p;
    }

    // Guarda o filtro na URL: dá para atualizar a página ou mandar o link sem perder a consulta
    function salvarFiltrosNaUrl() {
        const p = parametrosDoFiltro();
        p.set('agrupamento', el('fAgrupamento').value);
        history.replaceState(null, '', location.pathname + '?' + p.toString());
    }

    function lerFiltrosDaUrl() {
        const p = new URLSearchParams(location.search);
        if (p.get('inicio')) el('fInicio').value = p.get('inicio');
        if (p.get('fim')) el('fFim').value = p.get('fim');
        if (p.get('modalidade')) el('fModalidade').value = p.get('modalidade');
        if (p.get('produtoId')) el('fProduto').value = p.get('produtoId');
        if (p.get('agrupamento')) el('fAgrupamento').value = p.get('agrupamento');
        p.getAll('status').forEach((s) => {
            const caixa = el('st_' + s);
            if (caixa) caixa.checked = true;
        });
        atualizarRotuloStatus();
    }

    function ligarEventos() {
        el('formFiltros').addEventListener('submit', (e) => {
            e.preventDefault();
            if (el('fInicio').value && el('fFim').value && el('fInicio').value > el('fFim').value) {
                mostrarAlerta('warning', 'A data inicial não pode ser depois da final.');
                return;
            }
            carregarTudo();
        });
        el('btnLimpar').addEventListener('click', () => {
            el('formFiltros').reset();
            document.querySelectorAll('#listaStatus input').forEach((c) => { c.checked = false; });
            atualizarRotuloStatus();
            el('fInicio').value = opcoes.inicioPadrao;
            el('fFim').value = opcoes.fimPadrao;
            carregarTudo();
        });
        document.querySelectorAll('[data-periodo]').forEach((botao) => {
            botao.addEventListener('click', () => {
                aplicarAtalhoDePeriodo(botao.dataset.periodo);
                carregarTudo();
            });
        });
        el('fAgrupamento').addEventListener('change', () => carregarVendas().then(salvarFiltrosNaUrl));
        document.querySelectorAll('input[name="criterio"]').forEach((r) => r.addEventListener('change', carregarRanking));
        el('btnAtualizar').addEventListener('click', carregarTudo);
    }

    function aplicarAtalhoDePeriodo(periodo) {
        const hoje = new Date(opcoes.fimPadrao + 'T12:00:00');
        const inicio = new Date(hoje);
        if (periodo === 'mes') {
            inicio.setDate(1);
        } else if (periodo !== 'hoje') {
            inicio.setDate(hoje.getDate() - (Number(periodo) - 1));
        }
        el('fInicio').value = paraIso(inicio);
        el('fFim').value = paraIso(hoje);
    }

    function paraIso(data) {
        return data.getFullYear() + '-' + String(data.getMonth() + 1).padStart(2, '0') + '-' + String(data.getDate()).padStart(2, '0');
    }

    // Chamadas à API (RF06) com tratamento de erros

    async function buscar(url) {
        const resposta = await fetch(url, { headers: { Accept: 'application/json' }, credentials: 'same-origin' });
        if (resposta.status === 401) {
            location.href = '/login';
            throw new Error('Sessão expirada.');
        }
        if (!resposta.ok) {
            // A API devolve erros no formato Problem Details: { title, detail, status }
            let detalhe = 'erro ' + resposta.status;
            try {
                const problema = await resposta.json();
                detalhe = problema.detail || problema.title || detalhe;
            } catch (e) { /* resposta sem JSON */ }
            throw new Error(detalhe);
        }
        return resposta.json();
    }

    async function carregarTudo() {
        if (carregando) return;
        carregando = true;
        limparAlertas();
        Object.values(graficos).forEach((g) => g.showLoading({ text: 'Carregando...' }));

        // Cada bloco carrega de forma independente: se um falhar, os outros continuam aparecendo
        const resultados = await Promise.allSettled([
            carregarResumo(), carregarVendas(), carregarSituacao(), carregarRanking(), carregarHoras(), carregarCanais()
        ]);
        const falhas = resultados.filter((r) => r.status === 'rejected').map((r) => r.reason.message);
        if (falhas.length > 0) {
            mostrarAlerta('danger', [...new Set(falhas)].join(' '));
        }
        Object.values(graficos).forEach((g) => g.hideLoading());
        el('ultimaAtualizacao').textContent = hora.format(new Date());
        salvarFiltrosNaUrl();
        carregando = false;
    }

    // RF01: indicadores

    async function carregarResumo() {
        const r = await buscar(`${API}/resumo?${parametrosDoFiltro()}`);
        el('kFaturamento').textContent = moeda.format(r.faturamento);
        el('kPedidos').textContent = numero.format(r.totalPedidos);
        el('kTicket').textContent = moeda.format(r.ticketMedio);
        el('kDescontos').textContent = 'Descontos: ' + moeda.format(r.totalDescontos);
        el('kItens').textContent = numero.format(r.itensVendidos);
        el('kClientes').textContent = numero.format(r.clientesAtendidos) + ' cliente(s) atendido(s)';
        el('kCancelados').textContent = numero.format(r.pedidosCancelados) + ' (' + formatarPercentual(r.taxaCancelamento) + ')';
        el('kValorCancelado').textContent = r.valorCancelado > 0 ? moeda.format(r.valorCancelado) + ' perdidos' : '';
        el('kTempo').textContent = r.tempoMedioAteSaidaMinutos == null ? '—' : r.tempoMedioAteSaidaMinutos + ' min';
        mostrarVariacao(el('kFaturamentoVar'), r.comparativo.variacaoFaturamento, r.comparativo);
        mostrarVariacao(el('kPedidosVar'), r.comparativo.variacaoPedidos, r.comparativo);
    }

    function mostrarVariacao(alvo, variacao, comparativo) {
        const periodo = formatarData(comparativo.inicioAnterior) + ' a ' + formatarData(comparativo.fimAnterior);
        alvo.title = 'Comparado com ' + periodo;
        if (variacao == null) {
            alvo.className = 'small text-muted';
            alvo.textContent = 'sem base no período anterior';
            return;
        }
        const subiu = variacao >= 0;
        alvo.className = 'small ' + (subiu ? 'text-success' : 'text-danger');
        alvo.textContent = (subiu ? '▲ ' : '▼ ') + formatarPercentual(Math.abs(variacao)) + ' vs. período anterior';
    }

    // RF02: vendas no tempo (barras = pedidos, linha = faturamento)

    async function carregarVendas() {
        const p = parametrosDoFiltro();
        p.set('agrupamento', el('fAgrupamento').value || 'DIA');
        const serie = await buscar(`${API}/vendas?${p}`);
        el('picoVendas').textContent = serie.pico
            ? 'Melhor resultado: ' + serie.pico.rotulo + ' com ' + moeda.format(serie.pico.faturamento)
            : 'Sem vendas no período.';
        desenhar('graficoVendas', {
            tooltip: {
                trigger: 'axis',
                formatter: (itens) => escapar(itens[0].axisValue) + itens.map((i) =>
                    '<br>' + i.marker + i.seriesName + ': ' + (i.seriesName === 'Faturamento' ? moeda.format(i.value) : numero.format(i.value))).join('')
            },
            legend: { data: ['Faturamento', 'Pedidos'] },
            grid: { left: 60, right: 50, bottom: 30, top: 40 },
            xAxis: { type: 'category', data: serie.pontos.map((p) => p.rotulo) },
            yAxis: [
                { type: 'value', name: 'R$', axisLabel: { formatter: (v) => numero.format(v) } },
                { type: 'value', name: 'Pedidos', minInterval: 1 }
            ],
            series: [
                { name: 'Faturamento', type: 'line', smooth: true, areaStyle: { opacity: 0.15 },
                  data: serie.pontos.map((p) => Number(p.faturamento)) },
                { name: 'Pedidos', type: 'bar', yAxisIndex: 1, barMaxWidth: 18, itemStyle: { opacity: 0.6 },
                  data: serie.pontos.map((p) => p.pedidos) }
            ]
        });
    }

    // Horário de pico: mesmo endpoint, agrupado por hora
    async function carregarHoras() {
        const p = parametrosDoFiltro();
        p.set('agrupamento', 'HORA_DO_DIA');
        const serie = await buscar(`${API}/vendas?${p}`);
        desenhar('graficoHoras', {
            tooltip: { trigger: 'axis' },
            grid: { left: 40, right: 10, bottom: 30, top: 20 },
            xAxis: { type: 'category', data: serie.pontos.map((p) => p.rotulo) },
            yAxis: { type: 'value', minInterval: 1 },
            series: [{ name: 'Pedidos', type: 'bar', data: serie.pontos.map((p) => p.pedidos), itemStyle: { color: '#fd7e14' } }]
        });
    }

    // RF03: situação dos pedidos + fila

    async function carregarSituacao() {
        const s = await buscar(`${API}/pedidos/situacao?${parametrosDoFiltro()}`);
        const comPedido = s.porStatus.filter((x) => x.quantidade > 0);
        desenhar('graficoStatus', comPedido.length === 0 ? vazio('Nenhum pedido no período') : {
            tooltip: { trigger: 'item', formatter: (i) => `${i.name}: ${i.value} (${i.percent}%)` },
            legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
            series: [{
                type: 'pie', radius: ['40%', '62%'], center: ['50%', '42%'],
                minShowLabelAngle: 10, // fatias muito pequenas ficam só na legenda, sem rótulo embolado
                label: { formatter: '{c}' },
                data: comPedido.map((x) => ({ name: x.descricao, value: x.quantidade, itemStyle: { color: CORES_STATUS[x.status] } }))
            }]
        });
        el('limiteAtraso').textContent = '(em vermelho: abertos há mais de ' + s.limiteAtrasoMinutos + ' min)';
        desenharFila(s.emAndamento);
    }

    function desenharFila(fila) {
        const corpo = el('tabelaFila');
        corpo.innerHTML = '';
        if (fila.length === 0) {
            const linha = corpo.insertRow();
            const celula = linha.insertCell();
            celula.colSpan = 6;
            celula.className = 'text-center text-muted py-4';
            celula.textContent = 'Nenhum pedido aberto no momento.';
            return;
        }
        fila.forEach((p) => {
            const linha = corpo.insertRow();
            if (p.atrasado) linha.className = 'table-danger';
            // textContent em vez de innerHTML: nome de cliente nunca vira código na página
            [['ps-3 text-muted', '#' + p.codigo], ['', p.cliente || '—'], ['', p.statusDescricao],
             ['', dataHora.format(new Date(p.entradaEm))], ['', p.minutosEmAberto + ' min' + (p.atrasado ? ' ⚠' : '')],
             ['pe-3 text-end', moeda.format(p.valorTotal)]]
                .forEach(([classe, texto]) => {
                    const c = linha.insertCell();
                    c.className = classe;
                    c.textContent = texto;
                });
        });
    }

    // RF04: ranking de produtos

    function parametrosDoRanking() {
        const p = parametrosDoFiltro();
        p.set('criterio', document.querySelector('input[name="criterio"]:checked').value);
        p.set('limite', '5');
        return p;
    }

    async function carregarRanking() {
        const p = parametrosDoRanking();
        const r = await buscar(`${API}/produtos/ranking?${p}`);
        const porValor = r.criterio === 'FATURAMENTO';
        // O ECharts desenha de baixo para cima, então invertemos para o 1º ficar no topo
        const produtos = [...r.produtos].reverse();
        desenhar('graficoRanking', produtos.length === 0 ? vazio('Sem vendas no período') : {
            tooltip: {
                trigger: 'axis', axisPointer: { type: 'shadow' },
                formatter: (itens) => {
                    const prod = produtos[itens[0].dataIndex];
                    return `${prod.posicao}º ${escapar(prod.nome)}<br>${numero.format(prod.quantidade)} un. · ${moeda.format(prod.faturamento)}<br>${formatarPercentual(prod.participacao)} do total`;
                }
            },
            grid: { left: 10, right: 60, bottom: 10, top: 10, containLabel: true },
            xAxis: { type: 'value', show: false },
            yAxis: { type: 'category', data: produtos.map((x) => x.posicao + 'º ' + x.nome) },
            series: [{
                type: 'bar', barMaxWidth: 22,
                data: produtos.map((x) => (porValor ? Number(x.faturamento) : x.quantidade)),
                label: { show: true, position: 'right', formatter: (i) => (porValor ? moeda.format(i.value) : numero.format(i.value) + ' un.') },
                itemStyle: { color: '#198754' }
            }]
        });
    }

    // Canais: entrega x retirada

    async function carregarCanais() {
        const canais = await buscar(`${API}/canais?${parametrosDoFiltro()}`);
        const comPedido = canais.filter((c) => c.pedidos > 0);
        desenhar('graficoCanais', comPedido.length === 0 ? vazio('Nenhuma saída no período') : {
            tooltip: { trigger: 'item', formatter: (i) => `${i.name}: ${i.value} (${i.percent}%)` },
            legend: { bottom: 0 },
            series: [{
                type: 'pie', radius: '60%', center: ['50%', '42%'],
                label: { position: 'inside', formatter: '{d}%', color: '#fff', fontWeight: 'bold' },
                data: comPedido.map((c) => ({ name: c.descricao, value: c.pedidos }))
            }]
        });
    }

    // Tempo real (RF07): o servidor avisa quando um pedido muda

    function conectarTempoReal() {
        if (!('EventSource' in window)) {
            ativarAtualizacaoDeReserva();
            return;
        }
        const fonte = new EventSource(`${API}/eventos`);
        fonte.addEventListener('open', () => {
            mostrarConexao(true);
            pararAtualizacaoDeReserva();
        });
        fonte.addEventListener('pedido-alterado', (evento) => {
            let dados = {};
            try { dados = JSON.parse(evento.data); } catch (e) { /* aviso sem dados */ }
            avisarMudanca(dados);
            // espera um pouco para juntar mudanças seguidas (ex.: vários cliques do gerente)
            clearTimeout(timerRecarga);
            timerRecarga = setTimeout(carregarTudo, ESPERA_ANTES_DE_RECARREGAR_MS);
        });
        fonte.addEventListener('error', () => {
            // O navegador tenta reconectar sozinho; enquanto isso, atualizamos de tempos em tempos
            mostrarConexao(false);
            ativarAtualizacaoDeReserva();
        });
    }

    function mostrarConexao(aoVivo) {
        const badge = el('statusConexao');
        badge.className = 'badge ' + (aoVivo ? 'bg-success' : 'bg-warning text-dark');
        badge.innerHTML = '<i class="bi bi-circle-fill me-1"></i>' + (aoVivo ? 'Ao vivo' : 'Reconectando...');
    }

    function ativarAtualizacaoDeReserva() {
        if (!timerReserva) {
            timerReserva = setInterval(carregarTudo, INTERVALO_RESERVA_MS);
        }
    }

    function pararAtualizacaoDeReserva() {
        clearInterval(timerReserva);
        timerReserva = null;
    }

    function avisarMudanca(dados) {
        const nomes = {
            CRIACAO_PEDIDO: 'Novo pedido', MUDANCA_STATUS_PEDIDO: 'Pedido mudou de status',
            DESCONTO_PEDIDO: 'Desconto aplicado', CANCELAMENTO_PEDIDO: 'Pedido cancelado'
        };
        const codigo = dados.pedidoId ? ' #' + String(dados.pedidoId).slice(-6) : '';
        mostrarAlerta('info', (nomes[dados.operacao] || 'Pedido alterado') + codigo + ' — atualizando o dashboard...', 6000);
    }

    // Utilitários

    function desenhar(id, configuracao) {
        const grafico = graficos[id];
        if (grafico) grafico.setOption(configuracao, true);
    }

    function vazio(texto) {
        return { title: { text: texto, left: 'center', top: 'middle', textStyle: { color: '#6c757d', fontSize: 14, fontWeight: 'normal' } } };
    }

    function formatarPercentual(valor) {
        return numero.format(Number(valor)) + '%';
    }

    function formatarData(iso) {
        const [a, m, d] = iso.split('-');
        return `${d}/${m}/${a}`;
    }

    function escapar(texto) {
        const div = document.createElement('div');
        div.textContent = texto == null ? '' : texto;
        return div.innerHTML;
    }

    // Limpa só os avisos de erro; o aviso azul de "pedido alterado" some sozinho
    function limparAlertas() {
        el('alertas').querySelectorAll('.alert-danger, .alert-warning').forEach((a) => a.remove());
    }

    function mostrarAlerta(tipo, mensagem, sumirEmMs) {
        const alerta = document.createElement('div');
        alerta.className = `alert alert-${tipo} alert-dismissible fade show py-2 small`;
        alerta.setAttribute('role', 'alert');
        alerta.textContent = mensagem;
        const fechar = document.createElement('button');
        fechar.type = 'button';
        fechar.className = 'btn-close';
        fechar.setAttribute('data-bs-dismiss', 'alert');
        alerta.append(fechar);
        el('alertas').append(alerta);
        if (sumirEmMs) setTimeout(() => alerta.remove(), sumirEmMs);
    }
})();
