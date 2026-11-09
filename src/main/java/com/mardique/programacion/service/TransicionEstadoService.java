package com.mardique.programacion.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.mardique.programacion.domain.EstadoProgramacion;

/**
 * Único lugar donde se decide qué transiciones de estado son válidas.
 *
 * <p>El estado vive en {@link EstadoProgramacion}, pero las reglas de quién puede
 * pasar de un estado a otro, y desde cuál, están todas aquí. Centralizarlas
 * importa por dos motivos: una pantalla no puede inventarse su propia máquina de
 * estados, y agregar un paso nuevo (por ejemplo una revisión de Operaciones)
 * no obliga a tocar cinco clases.
 *
 * <p>Ninguna regla mira el rol: eso lo aplica la seguridad, por anotación y por
 * alcance de área. Aquí solo hay una pregunta: <em>¿este cambio de estado tiene
 * sentido desde donde está la programación?</em>
 */
@Service
public class TransicionEstadoService {

	private static final Map<EstadoProgramacion, Set<EstadoProgramacion>> PERMITIDAS = permitidas();

	/**
	 * @param origen  estado actual.
	 * @param destino estado al que se quiere pasar.
	 * @return {@code true} si la transición está permitida.
	 */
	public boolean permitida(EstadoProgramacion origen, EstadoProgramacion destino) {
		return PERMITIDAS.getOrDefault(origen, Set.of()).contains(destino);
	}

	/**
	 * @param origen estado actual.
	 * @return estados alcanzables desde ahí.
	 */
	public Set<EstadoProgramacion> siguientes(EstadoProgramacion origen) {
		return PERMITIDAS.getOrDefault(origen, Set.of());
	}

	/**
	 * Exige una transición válida.
	 *
	 * @throws TransicionNoValidaException si el cambio no está permitido.
	 */
	public void exigir(EstadoProgramacion origen, EstadoProgramacion destino) {
		if (!permitida(origen, destino)) {
			throw new TransicionNoValidaException(origen, destino, siguientes(origen));
		}
	}

	/**
	 * El flujo de una propuesta.
	 *
	 * <p>Lo normal es: el coordinador la deja en {@code BORRADOR}, la envía, TH la
	 * revisa y termina en {@code APROBADA}, {@code AJUSTADA} o {@code RECHAZADA}.
	 * De ahí se avisa, se ejecuta y se cierra.
	 *
	 * <p>Se puede cancelar desde casi cualquier punto, porque el mundo cambia: la
	 * operación se cae, la persona se enferma. Pero no se puede cancelar algo ya
	 * cerrado, porque ese registro es histórico.
	 *
	 * <p>De {@code ENVIADA} se puede ir directo a las tres decisiones. Exigir un
	 * paso intermedio de "la estoy revisando" solo para poder aprobarla
	 * convertiría una decisión en dos clics, y el primero es el que se olvida.
	 * {@code EN_REVISION_TH} sigue existiendo para cuando de verdad se quiera
	 * reclamar el caso y dejarlo abierto, no resolverlo en el acto.
	 */
	private static Map<EstadoProgramacion, Set<EstadoProgramacion>> permitidas() {
		Map<EstadoProgramacion, Set<EstadoProgramacion>> mapa = new EnumMap<>(EstadoProgramacion.class);

		mapa.put(EstadoProgramacion.BORRADOR, EnumSet.of(EstadoProgramacion.ENVIADA,
				EstadoProgramacion.CANCELADA));
		mapa.put(EstadoProgramacion.ENVIADA, EnumSet.of(EstadoProgramacion.EN_REVISION_TH,
				EstadoProgramacion.APROBADA, EstadoProgramacion.AJUSTADA,
				EstadoProgramacion.RECHAZADA, EstadoProgramacion.CANCELADA));
		mapa.put(EstadoProgramacion.EN_REVISION_TH, EnumSet.of(EstadoProgramacion.APROBADA,
				EstadoProgramacion.AJUSTADA, EstadoProgramacion.RECHAZADA, EstadoProgramacion.CANCELADA));
		mapa.put(EstadoProgramacion.APROBADA, EnumSet.of(EstadoProgramacion.NOTIFICADA,
				EstadoProgramacion.CANCELADA, EstadoProgramacion.EN_REVISION_TH));
		mapa.put(EstadoProgramacion.AJUSTADA, EnumSet.of(EstadoProgramacion.NOTIFICADA,
				EstadoProgramacion.CANCELADA, EstadoProgramacion.EN_REVISION_TH));
		mapa.put(EstadoProgramacion.RECHAZADA, EnumSet.of(EstadoProgramacion.ENVIADA,
				EstadoProgramacion.CANCELADA));
		mapa.put(EstadoProgramacion.NOTIFICADA, EnumSet.of(EstadoProgramacion.EJECUTADA,
				EstadoProgramacion.CANCELADA));
		mapa.put(EstadoProgramacion.EJECUTADA, EnumSet.of(EstadoProgramacion.CERRADA,
				EstadoProgramacion.CANCELADA));
		mapa.put(EstadoProgramacion.CERRADA, EnumSet.noneOf(EstadoProgramacion.class));
		mapa.put(EstadoProgramacion.CANCELADA, EnumSet.noneOf(EstadoProgramacion.class));

		return Map.copyOf(mapa);
	}
}
