package com.umc.pediupatrao.entity;

import java.util.Map;

/**
 * Toda entidade que entra na auditoria sabe tirar uma "foto" de si mesma.
 *
 * A ideia é simples: tiramos uma foto antes da alteração e outra depois.
 * A auditoria compara as duas e grava só os campos que mudaram.
 * Assim nenhuma senha ou campo interno vaza, porque só entra na foto
 * o que a própria entidade decidiu mostrar.
 */
public interface Auditavel {

    Map<String, String> retratoParaAuditoria();
}
