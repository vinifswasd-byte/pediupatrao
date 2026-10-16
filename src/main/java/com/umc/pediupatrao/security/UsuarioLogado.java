package com.umc.pediupatrao.security;

import com.umc.pediupatrao.entity.Perfil;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Atalho para descobrir quem está usando o sistema neste momento.
 * Os services usam isso para gravar "quem fez" nos pedidos e na auditoria,
 * sem confiar em nenhum nome vindo do navegador.
 */
@Component
public class UsuarioLogado {

    public static final String ANONIMO = "anonimo";

    public String nome() {
        Authentication auth = autenticacaoAtual();
        return auth == null ? ANONIMO : auth.getName();
    }

    /** Devolve o perfil sem o prefixo ROLE_ (ex.: GERENTE). */
    public String perfil() {
        Authentication auth = autenticacaoAtual();
        if (auth == null) {
            return "SEM_PERFIL";
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst()
                .orElse("SEM_PERFIL");
    }

    public boolean temPerfil(Perfil perfil) {
        return perfil.name().equals(perfil());
    }

    private Authentication autenticacaoAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth;
    }
}
