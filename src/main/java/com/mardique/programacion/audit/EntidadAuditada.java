package com.mardique.programacion.audit;

/**
 * Entidades que se auditan. El nombre persistido es el de la tabla, para poder
 * correlacionar la bitácora con la base sin una tabla de equivalencias.
 */
public enum EntidadAuditada {

	AREA("area"),

	CARGO("cargo"),

	TURNO("turno"),

	COLABORADOR("colaborador"),

	COLABORADOR_SALARIO("colaborador_salario"),

	COLABORADOR_HABILITACION("colaborador_habilitacion"),

	COLABORADOR_NO_DISPONIBLE("colaborador_no_disponible"),

	HABILITACION("habilitacion"),

	TIPO_OPERACION("tipo_operacion"),

	TIPO_OPERACION_HABILITACION("tipo_operacion_habilitacion"),

	USUARIO("usuario"),

	USUARIO_AREA("usuario_area"),

	PARAMETRO_LABORAL("parametro_laboral"),

	PROGRAMACION_EXTENDIDO("programacion_extendido"),

	CORTE_NOMINA("corte_nomina"),

	HORA_EXTRA_EJECUTADA("hora_extra_ejecutada"),

	NOTIFICACION_SMS("notificacion_sms"),

	VEHICULO("vehiculo"),

	DESTINO("destino"),

	VIAJE_TRANSPORTE("viaje_transporte"),

	VIAJE_PASAJERO("viaje_pasajero"),

	HORARIO_FRECUENTE("horario_frecuente"),

	LISTA_ALIMENTACION("lista_alimentacion"),

	MAQUINARIA("maquinaria"),

	NOVEDAD("novedad");

	private final String nombreTabla;

	EntidadAuditada(String nombreTabla) {
		this.nombreTabla = nombreTabla;
	}

	public String nombreTabla() {
		return nombreTabla;
	}
}
