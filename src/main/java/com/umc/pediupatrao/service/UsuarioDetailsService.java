package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Chamado pelo Spring Security no login.
 * Antes todo mundo entrava como USER. Agora o perfil gravado no banco vira a role
 * de verdade (ROLE_ADMIN, ROLE_GERENTE ou ROLE_ATENDENTE).
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return usuarioRepository.findByUsername(username)
                .map(usuario -> {
                    Perfil perfil = Perfil.de(usuario.getRole());
                    // Usuário com perfil desconhecido entra sem nenhuma permissão:
                    // consegue logar, mas toda tela protegida devolve 403.
                    List<SimpleGrantedAuthority> permissoes = perfil == null
                            ? List.of()
                            : List.of(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
                    return User.withUsername(usuario.getUsername())
                            .password(usuario.getPassword())
                            .authorities(permissoes)
                            .build();
                })
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}
