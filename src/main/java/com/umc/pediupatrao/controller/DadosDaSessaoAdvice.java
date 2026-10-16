package com.umc.pediupatrao.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Coloca em todas as telas quem está logado e com qual perfil.
 * A sidebar usa isso para mostrar "Logado como ..." e o botão Sair,
 * o que também ajuda a deixar claro no vídeo qual perfil está em uso.
 */
@ControllerAdvice
public class DadosDaSessaoAdvice {

    public record Sessao(String usuario, String perfil) {
    }

    @ModelAttribute("sessao")
    public Sessao sessao(Authentication autenticacao) {
        if (autenticacao == null) {
            return null;
        }
        String perfil = autenticacao.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst()
                .orElse("SEM PERFIL");
        return new Sessao(autenticacao.getName(), perfil);
    }
}
