package com.mardique.programacion.service.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.mardique.programacion.domain.TipoProgramacion;

/**
 * Ficha de programación preparada por la capa de aplicación para precarga del
 * formulario del coordinador.
 *
 * <p>Contiene los mismos campos que el formulario web pero sin anotaciones de
 * validación ni de binding HTTP: esas restricciones pertenecen a la capa de
 * presentación y no al dominio de la aplicación. La conversión
 * FormularioProgramacion ↔ FichaProgramacion es responsabilidad del
 * controlador.
 *
 * <p>Usada como elemento de {@link FichasPreparadas} para los atajos "repetir
 * día" y "selección masiva".
 */
public class FichaProgramacion {

	private Long areaId;
	private Long colaboradorId;
	private TipoProgramacion tipo;
	private Long tipoOperacionId;
	private LocalDate fecha;
	private LocalTime horaInicio;
	private LocalTime horaFin;
	/** Solo cuando el turno termina en un día posterior al de inicio. */
	private LocalDate fechaFin;
	private String motivo;

	public Long getAreaId() { return areaId; }
	public void setAreaId(Long areaId) { this.areaId = areaId; }

	public Long getColaboradorId() { return colaboradorId; }
	public void setColaboradorId(Long colaboradorId) { this.colaboradorId = colaboradorId; }

	public TipoProgramacion getTipo() { return tipo; }
	public void setTipo(TipoProgramacion tipo) { this.tipo = tipo; }

	public Long getTipoOperacionId() { return tipoOperacionId; }
	public void setTipoOperacionId(Long tipoOperacionId) { this.tipoOperacionId = tipoOperacionId; }

	public LocalDate getFecha() { return fecha; }
	public void setFecha(LocalDate fecha) { this.fecha = fecha; }

	public LocalTime getHoraInicio() { return horaInicio; }
	public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }

	public LocalTime getHoraFin() { return horaFin; }
	public void setHoraFin(LocalTime horaFin) { this.horaFin = horaFin; }

	public LocalDate getFechaFin() { return fechaFin; }
	public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }

	public String getMotivo() { return motivo; }
	public void setMotivo(String motivo) { this.motivo = motivo; }
}
