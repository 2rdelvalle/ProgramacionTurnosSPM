package com.mardique.programacion.audit;

/**
 * Vocabulario de acciones auditadas. Centralizarlo evita que cada servicio
 * invente su propia cadena y deja la búsqueda en la bitácora consistente.
 *
 * <p>Los textos se guardan tal cual en {@code auditoria.accion}, así que
 * cambiarlos después rompería los filtros ya consultados.
 */
public enum AccionAuditada {

	CREAR,

	EDITAR,

	ELIMINAR,

	APROBAR,

	AJUSTAR,

	RECHAZAR,

	CANCELAR,

	NOTIFICAR,

	/** La programación pasó a ejecutada: se confirmaron las horas reales. */
	EJECUTAR,

	/** Cierre definitivo: de una programación ejecutada o de un corte de nómina. */
	CERRAR,

	/** Intento de operar sobre un área fuera del alcance del usuario. */
	ACCESO_FUERA_DE_ALCANCE,

	/** Lectura de un dato sensible (salario, celular). */
	CONSULTAR_DATO_SENSIBLE,

	IMPORTAR,

	EXPORTAR
}
