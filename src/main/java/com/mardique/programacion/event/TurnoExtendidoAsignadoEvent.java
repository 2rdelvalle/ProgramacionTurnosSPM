package com.mardique.programacion.event;

import com.mardique.programacion.domain.ProgramacionExtendido;

/**
 * Una programación de turno pasó a {@code NOTIFICADA}.
 *
 * <p>El evento es el punto de enganche para todo lo que se hace al asignar un
 * turno sin acoplarlo al servicio: la cola de SMS, y más adelante cualquier
 * otro aviso que el alta dispare. Se publica después de guardar, para que el
 * oyente trabaje sobre la fila ya confirmada.
 *
 * @param programacion la programación recién notificada.
 */
public record TurnoExtendidoAsignadoEvent(ProgramacionExtendido programacion) {
}
