package com.mardique.programacion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vehículo de transporte de personal. La capacidad la valida la capa de
 * servicio al asignar pasajeros, no la base de datos.
 */
@Entity
@Table(name = "vehiculo")
@Getter
@Setter
@NoArgsConstructor
public class Vehiculo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "placa", nullable = false, length = 10, unique = true)
	private String placa;

	@Column(name = "capacidad", nullable = false)
	private Integer capacidad;

	@Column(name = "conductor", length = 120)
	private String conductor;

	/**
	 * Teléfono del conductor, para el reporte del viaje y para cualquier
	 * reclamo en ruta. Acompaña al nombre: sin conductor no se guarda.
	 */
	@Column(name = "telefono_conductor", length = 20)
	private String telefonoConductor;

	@Enumerated(EnumType.STRING)
	@Column(name = "propietario", length = 20)
	private PropietarioVehiculo propietario;

	/**
	 * Empresa que alquila el vehículo. Solo tiene sentido cuando el propietario es
	 * {@link PropietarioVehiculo#CONTRATADO}: la regla "contratado exige proveedor"
	 * la valida la capa de servicio al registrar, no la base de datos.
	 */
	@Column(name = "proveedor", length = 120)
	private String proveedor;

	@Column(name = "activo", nullable = false)
	private boolean activo = true;
}
