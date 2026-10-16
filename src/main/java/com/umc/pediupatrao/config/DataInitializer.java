package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Na primeira execução cria um usuário de cada perfil, para dar para testar
 * e gravar o vídeo logo de cara. Se o "admin" já existir com o perfil antigo
 * (USER ou vazio), ele é promovido para ADMIN.
 *
 * A senha inicial vem do application.properties. Troque depois pelo cadastro de usuários.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner prepararUsuariosIniciais(UsuarioRepository repo, PasswordEncoder encoder,
            @Value("${pediupatrao.usuarios-iniciais.senha:teste123}") String senhaInicial) {
        return args -> {
            garantirUsuario(repo, encoder, "admin", Perfil.ADMIN, senhaInicial);
            garantirUsuario(repo, encoder, "gerente01", Perfil.GERENTE, senhaInicial);
            garantirUsuario(repo, encoder, "atendente01", Perfil.ATENDENTE, senhaInicial);
        };
    }

    private void garantirUsuario(UsuarioRepository repo, PasswordEncoder encoder,
            String username, Perfil perfil, String senha) {
        repo.findByUsername(username).ifPresentOrElse(existente -> {
            if (Perfil.de(existente.getRole()) == null) {
                existente.setRole(perfil.name());
                repo.save(existente);
                log.info("Usuário {} estava sem perfil válido e foi ajustado para {}", username, perfil);
            }
        }, () -> {
            Usuario novo = new Usuario();
            novo.setUsername(username);
            novo.setRole(perfil.name());
            novo.setPassword(encoder.encode(senha));
            repo.save(novo);
            // Nunca logamos a senha, só o aviso para trocá-la.
            log.info("Usuário inicial {} ({}) criado. Lembre de trocar a senha padrão.", username, perfil);
        });
    }
}
