package com.umc.pediupatrao.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "usuarios") // Define a coleção no MongoDB
public class Usuario implements Auditavel {
    @Id
    private String id;
    private String username; // Nome de usuário para login

    // A senha pode chegar no JSON (para cadastrar), mas nunca é devolvida nas respostas da API.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password; // Senha criptografada

    // Continua sendo texto no banco para não quebrar cadastros antigos,
    // mas só aceitamos os valores do enum Perfil (ADMIN, GERENTE, ATENDENTE).
    private String role;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    /**
     * Foto para a auditoria. A senha fica de fora de propósito:
     * nem o hash dela deve aparecer em registro nenhum.
     */
    @Override
    public Map<String, String> retratoParaAuditoria() {
        Map<String, String> foto = new LinkedHashMap<>();
        foto.put("username", username);
        foto.put("perfil", role);
        foto.values().removeIf(v -> v == null);
        return foto;
    }
}
