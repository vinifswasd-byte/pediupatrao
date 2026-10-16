package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Usuario;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface UsuarioRepository extends MongoRepository<Usuario, String> {
    Optional<Usuario> findByUsername(String username);

    // Usado para não deixar o sistema ficar sem nenhum ADMIN.
    long countByRole(String role);
}
