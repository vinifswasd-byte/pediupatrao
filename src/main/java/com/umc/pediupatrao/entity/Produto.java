package com.umc.pediupatrao.entity;

import jakarta.persistence.Entity;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "produtos")
@Entity
public class Produto implements Auditavel {
    
    @Id
    private String id;
    private String nome;
    private String tipo; // Pizza, Esfiha, Bebida, Lanche
    private Double preco;
    private Boolean ativo;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Double getPreco() {
        return preco;
    }

    public void setPreco(Double preco) {
        this.preco = preco;
    }

    public Boolean getAtivo() {
        return ativo;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }

    /** Produto sem a marcação de ativo (cadastros antigos) é tratado como ativo. */
    public boolean disponivelParaVenda() {
        return !Boolean.FALSE.equals(ativo);
    }

    @Override
    public Map<String, String> retratoParaAuditoria() {
        Map<String, String> foto = new LinkedHashMap<>();
        foto.put("nome", nome);
        foto.put("tipo", tipo);
        foto.put("preco", preco == null ? null : String.valueOf(preco));
        foto.put("ativo", ativo == null ? null : String.valueOf(ativo));
        foto.values().removeIf(v -> v == null);
        return foto;
    }
}
