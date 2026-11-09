/* =====================================================================
   Mardique · Comportamiento de interfaz

   Tres cosas, y nada más:

   1. Confirmación antes de las acciones que no se pueden deshacer.
      Aprobar un día entero o rechazar una propuesta son decisiones con
      efecto sobre el personal, así que el doble clic accidental tiene que
      costar algo. Se declara en el HTML con data-confirm y lo intercepta
      este archivo; si el JavaScript no carga, el formulario se envía
      normal, porque una ventana de confirmación no puede ser la razón de
      que nadie pueda aprobar nada.

   2. Aviso de error al recargar el formulario con campos que corregir,
      usando mdqFocus.

   3. Cierre de los mensajes de éxito a los 5 s. Los de error se quedan:
      si algo falló, el motivo no puede desaparecer solo.

   Los diálogos replican el componente del Acta de Inspección Vehicular
   para que la confirmación se vea igual en los dos aplicativos.
   ===================================================================== */

(function () {
	"use strict";

	var reduceMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

	/* =================================================================
	   Diálogos: mdqAlert / mdqConfirm / mdqPrompt
	   ================================================================= */

	var ICONOS = {
		error: "fa-circle-exclamation",
		warning: "fa-triangle-exclamation",
		info: "fa-circle-info",
		success: "fa-circle-check",
		question: "fa-circle-question"
	};

	function abrir(opciones) {
		opciones = opciones || {};

		var capa = document.createElement("div");
		capa.className = "mdq-overlay";
		capa.setAttribute("role", "dialog");
		capa.setAttribute("aria-modal", "true");

		var cuerpoIcono;
		if (opciones.iconoImagen) {
			cuerpoIcono = '<img src="' + opciones.iconoImagen + '" alt="Mardique">';
		} else {
			cuerpoIcono = '<i class="fas ' + (ICONOS[opciones.icono] || ICONOS.info) + '"></i>';
		}

		capa.innerHTML =
			'<div class="mdq-card tipo-' + (opciones.icono || "info") + '">'
			+ '  <div class="mdq-head">'
			+ '    <div class="mdq-icon">' + cuerpoIcono + '</div>'
			+ '    <h3 class="mdq-title"></h3>'
			+ '  </div>'
			+ '  <p class="mdq-msg"></p>'
			+ '  <div class="mdq-actions"></div>'
			+ '</div>';

		capa.querySelector(".mdq-title").textContent = opciones.titulo || "Aviso";
		capa.querySelector(".mdq-msg").textContent = opciones.mensaje || "";

		var acciones = capa.querySelector(".mdq-actions");
		var entrada = null;

		if (opciones.entrada) {
			entrada = document.createElement("input");
			entrada.type = opciones.entrada.tipo || "text";
			entrada.className = "mdq-input";
			entrada.placeholder = opciones.entrada.placeholder || "";
			entrada.value = opciones.entrada.valor || "";
			acciones.parentNode.insertBefore(entrada, acciones);
			setTimeout(function () {
				entrada.focus();
			}, 60);
		}

		function cerrar(valor) {
			document.body.style.overflow = "";
			document.removeEventListener("keydown", alPulsarTecla);
			capa.classList.remove("open");
			setTimeout(function () {
				if (capa.parentNode) {
					capa.parentNode.removeChild(capa);
				}
			}, 260);
			if (opciones.resolver) {
				opciones.resolver(valor);
			}
		}

		function alPulsarTecla(evento) {
			if (evento.key === "Escape" && !opciones.entrada) {
				evento.preventDefault();
				cerrar(opciones.valorCancelar === undefined ? false : opciones.valorCancelar);
			}
		}

		var botonAceptar = document.createElement("button");
		botonAceptar.type = "button";
		botonAceptar.className = "btn-corp btn-confirm"
			+ (opciones.peligroso ? " danger" : "")
			+ (opciones.afirmativo ? " success" : "");
		botonAceptar.textContent = opciones.textoAceptar || "Aceptar";
		botonAceptar.addEventListener("click", function () {
			cerrar(entrada ? entrada.value : true);
		});

		if (!opciones.ocultarCancelar) {
			var botonCancelar = document.createElement("button");
			botonCancelar.type = "button";
			botonCancelar.className = "btn-corp btn-cancel";
			botonCancelar.textContent = opciones.textoCancelar || "Cancelar";
			botonCancelar.addEventListener("click", function () {
				cerrar(opciones.valorCancelar === undefined ? false : opciones.valorCancelar);
			});
			acciones.appendChild(botonCancelar);
		}

		acciones.appendChild(botonAceptar);

		capa.addEventListener("click", function (evento) {
			if (evento.target === capa) {
				cerrar(opciones.valorCancelar === undefined ? false : opciones.valorCancelar);
			}
		});

		document.body.appendChild(capa);
		document.body.style.overflow = "hidden";
		document.addEventListener("keydown", alPulsarTecla);
		if (!reduceMotion) {
			requestAnimationFrame(function () {
				capa.classList.add("open");
			});
		} else {
			capa.classList.add("open");
		}

		return { overlay: capa, input: entrada, cerrar: cerrar };
	}

	window.mdqAlert = function (mensaje, opciones) {
		opciones = opciones || {};
		return new Promise(function (resolver) {
			opciones.icono = opciones.icono || "info";
			opciones.textoAceptar = opciones.textoAceptar || "Entendido";
			opciones.ocultarCancelar = true;
			opciones.resolver = resolver;
			abrir(opciones);
		});
	};

	window.mdqConfirm = function (mensaje, opciones) {
		opciones = opciones || {};
		return new Promise(function (resolver) {
			opciones.icono = opciones.icono || "question";
			// Antes el ternario devolvía "Sí, continuar" en ambas ramas.
			// Ahora las acciones no peligrosas dicen "Aceptar" y solo las
			// peligrosas refuerzan el "Sí, continuar".
			opciones.textoAceptar = opciones.textoAceptar
				|| (opciones.peligroso ? "Sí, continuar" : "Aceptar");
			opciones.textoCancelar = opciones.textoCancelar || "Cancelar";
			opciones.resolver = resolver;
			abrir(opciones);
		});
	};

	window.mdqPrompt = function (mensaje, opciones) {
		opciones = opciones || {};
		return new Promise(function (resolver) {
			opciones.entrada = {
				tipo: opciones.tipoEntrada || "text",
				placeholder: opciones.placeholder || "",
				valor: opciones.valor || ""
			};
			opciones.icono = opciones.icono || "info";
			opciones.textoAceptar = opciones.textoAceptar || "Aceptar";
			opciones.valorCancelar = null;
			opciones.resolver = resolver;
			abrir(opciones);
		});
	};

	/* Resalta y enfoca el campo que hay que corregir. */
	window.mdqFocus = function (elemento) {
		if (!elemento) {
			return;
		}
		if (typeof elemento.scrollIntoView === "function") {
			elemento.scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth", block: "center" });
		}
		elemento.classList.remove("mdq-error-highlight");
		void elemento.offsetWidth;
		elemento.classList.add("mdq-error-highlight");
		var etiqueta = elemento.tagName ? elemento.tagName.toLowerCase() : "";
		if (etiqueta === "input" || etiqueta === "select" || etiqueta === "textarea") {
			setTimeout(function () {
				try {
					elemento.focus({ preventScroll: true });
				} catch (e) {
					elemento.focus();
				}
			}, 480);
		}
		setTimeout(function () {
			elemento.classList.remove("mdq-error-highlight");
		}, 3000);
	};

	/* =================================================================
	   Confirmación declarativa: <form data-confirm="...">

	   El atributo data-mdq-ok es la marca de que el diálogo ya se aceptó,
	   para que el segundo submit no vuelva a preguntar.

	   Atributos opcionales:
	     data-confirm-titulo      Texto del título
	     data-confirm-peligro     "true" → botón rojo (acción destructiva)
	     data-confirm-afirmativo  "true" → botón verde (acción positiva)
	     data-confirm-ok          Texto del botón de aceptar
	     data-confirm-cancelar    Texto del botón de cancelar
	   ================================================================= */

	document.addEventListener("submit", function (evento) {
		var formulario = evento.target;
		if (!formulario || !formulario.matches || !formulario.matches("[data-confirm]")) {
			return;
		}
		if (formulario.getAttribute("data-mdq-ok") === "1") {
			formulario.removeAttribute("data-mdq-ok");
			return;
		}
		evento.preventDefault();
		evento.stopPropagation();

		var esPeligroso = formulario.getAttribute("data-confirm-peligro") === "true";
		var esAfirmativo = formulario.getAttribute("data-confirm-afirmativo") === "true";

		window.mdqConfirm(formulario.getAttribute("data-confirm"), {
			titulo: formulario.getAttribute("data-confirm-titulo") || "Confirmar acción",
			icono: esPeligroso ? "warning" : (esAfirmativo ? "success" : "question"),
			peligroso: esPeligroso,
			afirmativo: esAfirmativo,
			textoAceptar: formulario.getAttribute("data-confirm-ok") || "Sí, continuar",
			textoCancelar: formulario.getAttribute("data-confirm-cancelar") || "Cancelar"
		}).then(function (aceptado) {
			if (aceptado) {
				formulario.setAttribute("data-mdq-ok", "1");
				formulario.submit();
			}
		});
	});

	/* =================================================================
	   Errores de validación
	   ================================================================= */

	function marcarErrores() {
		if (!document.querySelector) {
			return;
		}
		var primerError = document.querySelector(".campo--error");
		if (primerError) {
			window.mdqFocus(primerError);
		}
	}

	/* =================================================================
	   Barra superior: sombra al hacer scroll
	   ================================================================= */

	(function () {
		var barra = document.querySelector(".superior");
		if (!barra) {
			return;
		}
		var pendiente = false;
		function actualizar() {
			barra.classList.toggle("scrolled", window.scrollY > 4);
			pendiente = false;
		}
		window.addEventListener("scroll", function () {
			if (!pendiente) {
				requestAnimationFrame(actualizar);
				pendiente = true;
			}
		}, { passive: true });
		actualizar();
	})();

	/* =================================================================
	   Mensajes: los de éxito se van solos, los de error se quedan
	   ================================================================= */

	(function () {
		var alertas = document.querySelectorAll(".alerta");
		alertas.forEach(function (alerta) {
			if (alerta.classList.contains("alerta--error")) {
				return;
			}
			setTimeout(function () {
				alerta.classList.add("alerta--cerrando");
				setTimeout(function () {
					if (alerta.parentNode) {
						alerta.parentNode.removeChild(alerta);
					}
				}, 450);
			}, 5000);
		});
	})();

	/* =================================================================
	   Filtro de día: al elegir fecha se envía el formulario solo

	   El filtro de la pantalla de revisión solo tiene la fecha, así que
	   esperar al botón "Filtrar" no aporta nada.

	   Ojo con una trampa de la plataforma: formulario.submit() NO dispara
	   el evento submit, a diferencia de requestSubmit(). O sea que un
	   data-confirm puesto en este formulario no se vería. Por eso la
	   comprobación de "mdq-ok" de más abajo es inútil aquí y no se hace:
	   este formulario es un filtro GET sin confirmación, y si algún día
	   le ponen una, hay que cambiar a formulario.requestSubmit() para que
	   el manejador de confirmaciones la vea.
	   ================================================================= */

	document.addEventListener("change", function (evento) {
		if (evento.target && evento.target.matches && evento.target.matches("[data-filtro-dia]")) {
			var formulario = evento.target.form;
			if (!formulario) {
				return;
			}
			formulario.submit();
		}
	});

	/* =================================================================
	   Listas largas: 10 filas por página

	   Colaboradores, programaciones y auditoría pueden traer decenas de
	   filas, y de una sola pantalla no se ve nada. Se parten las filas ya
	   pintadas en el navegador en lugar de paginar en el servidor: así los
	   filtros de la página no se pierden, que es lo que obligaría a
	   repetirlos en cada enlace si la página viajara al controller.

	   Las tablas entran solas (table.tabla dentro de .tabla-envoltura).
	   Los listados por bloques —el de operaciones, que no es tabla porque
	   cada línea trae sus formularios— se declaran con data-paginar en su
	   contenedor. Ambos comparten esta barra y este POR_PAGINA.
	   ================================================================= */

	// El buscador de la misma lista avisa a su paginación para que vuelva a
	// la primera página con lo que todavía coincide.
	var refrescarPaginasBloques = [];

	(function () {
		var POR_PAGINA = 10;

		function paginar(tabla) {
			var cuerpo = tabla.tBodies[0];
			var envoltura = tabla.parentNode;
			if (!cuerpo || !envoltura || !envoltura.parentNode || !envoltura.classList
					|| !envoltura.classList.contains("tabla-envoltura")) {
				return;
			}

			var filas = Array.prototype.slice.call(cuerpo.rows);
			var vacia = null;
			var datos = filas.filter(function (fila) {
				if (fila.classList.contains("tabla--vacia")) {
					vacia = fila;
					return false;
				}
				return true;
			});

			if (datos.length <= POR_PAGINA) {
				return;
			}

			var totalPaginas = Math.ceil(datos.length / POR_PAGINA);
			var pagina = 1;

			var barra = document.createElement("div");
			barra.className = "paginacion";

			var atras = document.createElement("button");
			atras.type = "button";
			atras.className = "boton boton--fantasma boton--chico";
			atras.innerHTML = '<i class="fas fa-angle-left"></i> Anterior';

			var estado = document.createElement("span");
			estado.className = "paginacion__estado";

			var adelante = document.createElement("button");
			adelante.type = "button";
			adelante.className = "boton boton--fantasma boton--chico";
			adelante.innerHTML = 'Siguiente <i class="fas fa-angle-right"></i>';

			function pintar() {
				var desde = (pagina - 1) * POR_PAGINA;
				var hasta = desde + POR_PAGINA;
				datos.forEach(function (fila, indice) {
					fila.hidden = indice < desde || indice >= hasta;
				});
				if (vacia) {
					vacia.hidden = true;
				}
				estado.textContent = "Página " + pagina + " de " + totalPaginas;
				atras.disabled = pagina === 1;
				adelante.disabled = pagina === totalPaginas;
			}

			function mover(destino) {
				pagina = destino;
				pintar();
				barra.scrollIntoView({ block: "nearest" });
			}

			atras.addEventListener("click", function () {
				if (pagina > 1) {
					mover(pagina - 1);
				}
			});
			adelante.addEventListener("click", function () {
				if (pagina < totalPaginas) {
					mover(pagina + 1);
				}
			});

			barra.appendChild(atras);
			barra.appendChild(estado);
			barra.appendChild(adelante);
			// Va fuera de la envoltura: esa tiene overflow-x, y dentro de un
			// desplazamiento horizontal el botón de "Siguiente" no se vería.
			envoltura.parentNode.insertBefore(barra, envoltura.nextSibling);
			pintar();
		}

		/* ---------------------------------------------------------------
		   Listados por bloques (data-paginar=".clase" en el contenedor):
		   mismos 10 por página y misma barra que en las tablas. Cada bloque
		   recuerda si el buscador lo dejó fuera (data-coincide) para que
		   buscar y paginar no se peleen por la visibilidad de la fila.
		   --------------------------------------------------------------- */
		function paginarBloques(contenedor) {
			var selector = contenedor.getAttribute("data-paginar");
			var elementos = selector
					? Array.prototype.slice.call(contenedor.querySelectorAll(selector))
					: [];
			if (elementos.length <= POR_PAGINA) {
				return;
			}

			var pagina = 1;

			function coincide(elemento) {
				return elemento.dataset.coincide !== "0";
			}

			function totalPaginasDe() {
				var filtrados = elementos.filter(coincide).length;
				return Math.max(1, Math.ceil(filtrados / POR_PAGINA));
			}

			var barra = document.createElement("div");
			barra.className = "paginacion";

			var atras = document.createElement("button");
			atras.type = "button";
			atras.className = "boton boton--fantasma boton--chico";
			atras.innerHTML = '<i class="fas fa-angle-left"></i> Anterior';

			var estado = document.createElement("span");
			estado.className = "paginacion__estado";

			var adelante = document.createElement("button");
			adelante.type = "button";
			adelante.className = "boton boton--fantasma boton--chico";
			adelante.innerHTML = 'Siguiente <i class="fas fa-angle-right"></i>';

			function pintar() {
				var total = totalPaginasDe();
				if (pagina > total) {
					pagina = total;
				}
				var visibles = elementos.filter(coincide);
				var desde = (pagina - 1) * POR_PAGINA;
				elementos.forEach(function (elemento) {
					elemento.hidden = true;
				});
				visibles.slice(desde, desde + POR_PAGINA).forEach(function (elemento) {
					elemento.hidden = false;
				});
				estado.textContent = "Página " + pagina + " de " + total;
				atras.disabled = pagina === 1;
				adelante.disabled = pagina === total;
			}

			function mover(destino) {
				pagina = destino;
				pintar();
				barra.scrollIntoView({ block: "nearest" });
			}

			atras.addEventListener("click", function () {
				if (pagina > 1) {
					mover(pagina - 1);
				}
			});
			adelante.addEventListener("click", function () {
				if (pagina < totalPaginasDe()) {
					mover(pagina + 1);
				}
			});

			barra.appendChild(atras);
			barra.appendChild(estado);
			barra.appendChild(adelante);
			contenedor.parentNode.insertBefore(barra, contenedor.nextSibling);
			pintar();

			refrescarPaginasBloques.push(function () {
				pagina = 1;
				pintar();
			});
		}

		function iniciar() {
			document.querySelectorAll("table.tabla").forEach(paginar);
			document.querySelectorAll("[data-paginar]").forEach(paginarBloques);
		}

		if (document.readyState === "loading") {
			document.addEventListener("DOMContentLoaded", iniciar);
		} else {
			iniciar();
		}
	})();

	/* =================================================================
	   Buscadores declarativos: data-buscar-filas / data-buscar-select

	   El catálogo de operaciones pasó de veinte a más de cincuenta filas
	   y elegir "Descarga de maíz" a ciegas en un desplegable con la misma
	   cantidad de opciones se volvió el paso más lento de preparar una
	   programación. Son dos atributos, como el resto de la interfaz:

	     data-buscar-filas=".clase"   Oculta las filas de esa clase en la
	                                  página cuyo texto no coincida. La
	                                  nota [data-buscar-vacio] aparece
	                                  cuando no queda ninguna visible.

	     data-buscar-select           Oculta las opciones del <select> del
	                                  mismo campo. La primera opción vacía
	                                  (el "Sin operación…") solo se muestra
	                                  con el buscador limpio, para no
	                                  dejar el desplegable sin punto de
	                                  partida.

	   La comparación se hace en minúsculas y sin acentos: "maiz"
	   encuentra "Maíz". Si el JavaScript no carga, todo se ve completo:
	   un buscador nunca puede ser la razón de que no aparezca un dato.
	   ================================================================= */

	function normalizar(texto) {
		// NFD separa la letra de su tilde y el rango combinante la quita.
		return (texto || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");
	}

	function filtrarFilas(entrada) {
		var selector = entrada.getAttribute("data-buscar-filas");
		if (!selector) {
			return;
		}
		var texto = normalizar(entrada.value);
		var visibles = 0;
		document.querySelectorAll(selector).forEach(function (fila) {
			var coincide = texto === "" || normalizar(fila.textContent).indexOf(texto) !== -1;
			fila.hidden = !coincide;
			// Si la lista está paginada, la barra relee este dato al repartir.
			fila.dataset.coincide = coincide ? "1" : "0";
			if (coincide) {
				visibles++;
			}
		});
		refrescarPaginasBloques.forEach(function (refrescar) {
			refrescar();
		});
		var ambito = entrada.closest(".tarjeta__cuerpo") || document;
		var nota = ambito.querySelector("[data-buscar-vacio]");
		if (nota) {
			// La clase .vacio-nota fija display:block, así que con style
			// directo el estado "sin coincidencias" sí se puede ocultar.
			nota.style.display = (texto !== "" && visibles === 0) ? "block" : "none";
		}
	}

	function filtrarSelect(entrada) {
		var etiqueta = entrada.closest("label.campo") || entrada.parentNode;
		var select = etiqueta && etiqueta.querySelector ? etiqueta.querySelector("select") : null;
		if (!select) {
			return;
		}
		var texto = normalizar(entrada.value);
		Array.prototype.forEach.call(select.options, function (opcion, indice) {
			if (texto === "") {
				opcion.hidden = false;
				return;
			}
			if (indice === 0 && opcion.value === "") {
				opcion.hidden = true;
				return;
			}
			opcion.hidden = normalizar(opcion.textContent).indexOf(texto) === -1;
		});
	}

	document.addEventListener("input", function (evento) {
		var entrada = evento.target;
		if (!entrada || entrada.tagName !== "INPUT" || !entrada.type || entrada.type !== "search") {
			return;
		}
		if (entrada.hasAttribute("data-buscar-filas")) {
			filtrarFilas(entrada);
		} else if (entrada.hasAttribute("data-buscar-select")) {
			filtrarSelect(entrada);
		}
	});

	if (document.readyState === "loading") {
		document.addEventListener("DOMContentLoaded", marcarErrores);
	} else {
		marcarErrores();
	}
})();