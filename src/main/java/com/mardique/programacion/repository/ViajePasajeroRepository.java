package com.mardique.programacion.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.mardique.programacion.domain.ViajePasajero;

public interface ViajePasajeroRepository extends JpaRepository<ViajePasajero, Long> {

	/**
	 * Pasajeros de unos viajes, con el colaborador ya cargado. La capacidad del
	 * vehículo se valida en la capa de servicio contando estas filas.
	 *
	 * <p>La programación viaja en el mismo grafo porque la pantalla de transporte
	 * la usa para saber, sin otra consulta, a quién ya se le asignó vehículo.
	 */
	@EntityGraph(attributePaths = { "colaborador", "colaborador.area", "programacion" })
	List<ViajePasajero> findByViajeId(Long viajeId);

	@EntityGraph(attributePaths = { "colaborador", "viaje" })
	List<ViajePasajero> findByViajeIdIn(List<Long> viajeIds);

	boolean existsByViajeIdAndColaboradorId(Long viajeId, Long colaboradorId);

	/**
	 * El pasajero que quedó ligado a una programación, con el vehículo y el
	 * colaborador ya cargados.
	 *
	 * <p>Existe para poder desvincular a la persona de la salida: la pantalla
	 * conoce la programación, no el identificador del vínculo, y sin el vehículo
	 * en el grafo no habría forma de decir en el mensaje a qué placa se le quita.
	 */
	@EntityGraph(attributePaths = { "colaborador", "programacion", "programacion.area", "viaje", "viaje.vehiculo",
			"viaje.destino" })
	Optional<ViajePasajero> findByProgramacionId(Long programacionId);

	/**
	 * Los pasajeros de un listado de programaciones, con vehículo y destino ya
	 * cargados: el perfil del colaborador pinta el historial completo sin una
	 * consulta por turno.
	 */
	@EntityGraph(attributePaths = { "programacion", "viaje", "viaje.vehiculo", "viaje.destino" })
	List<ViajePasajero> findByProgramacionIdIn(Collection<Long> programacionIds);

	/** Quita todos los pasajeros de una salida antes de eliminarla. */
	void deleteByViajeId(Long viajeId);
}
