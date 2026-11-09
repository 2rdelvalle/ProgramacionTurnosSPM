package com.mardique.programacion.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mardique.programacion.domain.EstadoViaje;
import com.mardique.programacion.domain.ViajeTransporte;

public interface ViajeTransporteRepository extends JpaRepository<ViajeTransporte, Long> {

	/**
	 * Viajes activos de un vehículo en una hora exacta. La base de datos tiene un
	 * índice único parcial sobre (vehiculo, salida) que excluye los cancelados,
	 * así que esta consulta devuelve como máximo un registro: la aplicación debe
	 * usarla para dar al usuario horas libres en vez de dejar que la base rechace
	 * el insert.
	 */
	@EntityGraph(attributePaths = { "vehiculo", "destino" })
	List<ViajeTransporte> findByVehiculoIdAndSalidaAndEstadoNot(Long vehiculoId, LocalDateTime salida,
			EstadoViaje estado);

	/** Horario de un vehículo en una ventana, para mostrar sus viajes. */
	@EntityGraph(attributePaths = { "vehiculo", "destino" })
	List<ViajeTransporte> findByVehiculoIdAndSalidaBetweenOrderBySalidaAsc(Long vehiculoId,
			LocalDateTime desde, LocalDateTime hasta);

	/**
	 * Una salida con su vehículo y destino ya cargados.
	 *
	 * <p>La variante de {@code findById} sin más: la asignación masiva mira la
	 * placa y el cupo del vehículo DESPUÉS de salir de la transacción, y con el
	 * vehículo perezoso eso revienta con la sesión cerrada.
	 */
	@EntityGraph(attributePaths = { "vehiculo", "destino" })
	Optional<ViajeTransporte> findConRelacionesById(Long viajeId);

	/**
	 * Listado de salida de un periodo: viajes programados o ya realizados, en
	 * orden de hora de salida.
	 */
	@EntityGraph(attributePaths = { "vehiculo", "destino" })
	@Query("""
			select v from ViajeTransporte v
			where v.salida >= :desde
			  and v.salida <= :hasta
			  and v.estado <> :excluido
			order by v.salida asc
			""")
	List<ViajeTransporte> findListadoDeSalida(@Param("desde") LocalDateTime desde,
			@Param("hasta") LocalDateTime hasta, @Param("excluido") EstadoViaje excluido);

	@EntityGraph(attributePaths = { "vehiculo", "destino" })
	List<ViajeTransporte> findByEstadoOrderBySalidaAsc(EstadoViaje estado);

	/**
	 * Historial de salidas: todas las no canceladas, de la más reciente a la
	 * más antigua. No corta por fecha: el historial se abre desde cualquier
	 * día y debe mostrar todo lo registrado, con su vehículo y destino ya
	 * cargados.
	 */
	@EntityGraph(attributePaths = { "vehiculo", "destino" })
	List<ViajeTransporte> findByEstadoNotOrderBySalidaDesc(EstadoViaje estado);

	/**
	 * Todas las salidas de un vehículo, sin importar su estado.
	 *
	 * <p>Existe para el borrado del vehículo: las salidas cuelgan de él por clave
	 * ajena, así que se tienen que llevar puestas para poder quitar la placa del
	 * catálogo.
	 */
	@EntityGraph(attributePaths = { "vehiculo", "destino" })
	List<ViajeTransporte> findByVehiculoId(Long vehiculoId);

	/**
	 * Todas las salidas de un destino, sin importar su estado.
	 *
	 * <p>Existe por la misma razón que {@link #findByVehiculoId}: el destino
	 * también es clave ajena de la salida, y sin este listado no se podía quitar
	 * un destino del catálogo sin dejar salidas huérfanas.
	 */
	@EntityGraph(attributePaths = { "vehiculo", "destino" })
	List<ViajeTransporte> findByDestinoId(Long destinoId);
}
