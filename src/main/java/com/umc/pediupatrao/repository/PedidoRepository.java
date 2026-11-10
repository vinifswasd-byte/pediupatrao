package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import java.util.Collection;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface PedidoRepository extends MongoRepository<Pedido, String> {
    List<Pedido> findByClienteId(String clienteId);

    // Fila do dashboard: os 10 pedidos abertos mais antigos (o Spring monta a consulta pelo nome do método).
    List<Pedido> findTop10ByStatusInOrderByEntradaEmAsc(Collection<StatusPedido> status);
}
