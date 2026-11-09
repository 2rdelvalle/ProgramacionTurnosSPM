package com.mardique.programacion.domain;

/**
 * Tipo de alimento que recibe una persona extendida.
 *
 * <p>La clasificación no la elige el usuario: sale de las horas del programa.
 * La merienda es para quien termina a las 19:00 en punto (sale de tarde y se
 * lleva el refrigerio de las 4-5 pm); la cena es para quien entra a las 19:00
 * en adelante o trabaja pasadas las 19:00.
 */
public enum TipoAlimentacion {

	MERIENDA,

	CENA
}
