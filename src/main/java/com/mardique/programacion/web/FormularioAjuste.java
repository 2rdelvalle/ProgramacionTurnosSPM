package com.mardique.programacion.web;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

/**
 * Datos del ajuste que hace Talento Humano sobre una propuesta.
 *
 * <p>El motivo es obligatorio y no opcional: un ajuste sin explicación es
 * imposible de defender cuando la persona pregunta por qué salió antes, y es
 * justo el caso para el que este módulo existe.
 */
public class FormularioAjuste {

	/*
	 * El patrón es el que escribe y lee input[type=datetime-local]: con el formato
	 * corto de la localidad del servidor los campos abrirían vacíos en el
	 * navegador (que solo admite ISO) y lo que el revisor corrigiera podría
	 * perderse al guardar.
	 */
	@NotNull(message = "Indique la hora de inicio ajustada.")
	@DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
	private LocalDateTime inicio;

	@NotNull(message = "Indique la hora de fin ajustada.")
	@DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
	private LocalDateTime fin;

	@NotBlank(message = "Explique por qué se ajusta la propuesta.")
	@Size(max = 500, message = "El motivo puede tener hasta 500 caracteres.")
	private String motivo;

	@Size(max = 500, message = "El comentario puede tener hasta 500 caracteres.")
	private String comentario;

	public LocalDateTime getInicio() {
		return inicio;
	}

	public void setInicio(LocalDateTime inicio) {
		this.inicio = inicio;
	}

	public LocalDateTime getFin() {
		return fin;
	}

	public void setFin(LocalDateTime fin) {
		this.fin = fin;
	}

	public String getMotivo() {
		return motivo;
	}

	public void setMotivo(String motivo) {
		this.motivo = motivo;
	}

	public String getComentario() {
		return comentario;
	}

	public void setComentario(String comentario) {
		this.comentario = comentario;
	}
}
