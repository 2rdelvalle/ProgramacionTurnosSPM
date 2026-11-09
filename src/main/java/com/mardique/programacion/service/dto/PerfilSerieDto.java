package com.mardique.programacion.service.dto;

import java.math.BigDecimal;

/**
 * Un dato de las gráficas del perfil: etiqueta, valor y su porcentaje sobre
 * el máximo de la serie, ya calculado en el servidor para que la vista solo
 * pinte la altura (la cuenta no se repite en cada plantilla).
 */
public record PerfilSerieDto(String etiqueta, BigDecimal valor, int porcentaje) {
}
