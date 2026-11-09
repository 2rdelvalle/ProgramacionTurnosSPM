package com.mardique.programacion.security;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain filtroDeSeguridad(HttpSecurity http) throws Exception {

		// Handler plano: para Thymeleaf server-side es el más estable.
		// Con el Xor por defecto hay que sincronizar bien la cookie y el
		// input hidden; con el plano, lo que se pinta es lo que se valida.
		CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

		http
				.authorizeHttpRequests(pedidos -> pedidos
						.requestMatchers("/login", "/css/**", "/js/**", "/vendor/**", "/images/**",
								"/favicon.ico", "/error", "/403")
						.permitAll()
						// Actuator/health: publico solo para sondas de infraestructura.
						// El resto de endpoints de Actuator exige autenticacion.
						.requestMatchers("/actuator/health").permitAll()
						.requestMatchers("/actuator/**").authenticated()
						.anyRequest().authenticated())

				// Cabeceras de seguridad HTTP: X-Content-Type-Options, X-Frame-Options y
				// Referrer-Policy. Spring Security agrega X-XSS-Protection y Cache-Control
				// por defecto; aqui se completa con las que no vienen de serie.
				.headers(cab -> cab
						.contentTypeOptions(ct -> {})
						// DENY: no hay requisito documentado de embedding en iframe,
						// ni siquiera del mismo origen. Se mantiene el default restrictivo.
						// Cambiar a sameOrigin() solo con caso de uso aprobado por el arquitecto.
						.frameOptions(fo -> fo.deny())
						.referrerPolicy(rp ->
								rp.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))

				// Token CSRF en cookie XSRF-TOKEN (no usa HttpSession, así
				// que el render de /login tras el logout ya no intenta crear
				// una sesión sobre una respuesta comprometida).
			.csrf(csrf -> csrf
					.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
					.csrfTokenRequestHandler(csrfHandler))

			// Después de CsrfFilter el token ya está como atributo del
			// pedido; materializarlo acá garantiza que el Set-Cookie salga
			// antes de que el render comprometa la respuesta.
			.addFilterAfter(new CsrfTempranoFilter(), CsrfFilter.class)

				.formLogin(acceso -> acceso
						.loginPage("/login")
						.defaultSuccessUrl("/", true)
						.permitAll())

				.logout(salida -> salida
						.logoutUrl("/logout")
						.logoutSuccessHandler((req, res, auth) -> {
							res.setStatus(HttpServletResponse.SC_FOUND);
							res.setHeader("Location", req.getContextPath() + "/login?salir");
						})
						.invalidateHttpSession(true)
						.clearAuthentication(true)
						.deleteCookies("JSESSIONID")
						.permitAll())

				// Mismo forward a /403 que accessDeniedPage, pero con el motivo
			// en el log: si vuelve a aparecer un 403, ahí está el método, la
			// ruta y la causa (permiso faltante o token CSRF inválido).
			.exceptionHandling(errores -> errores.accessDeniedHandler((peticion, respuesta, excepcion) -> {
				log.warn("Acceso denegado: {} {} — {}", peticion.getMethod(), peticion.getRequestURI(),
						excepcion.getMessage());
				peticion.getRequestDispatcher("/403").forward(peticion, respuesta);
			}));

		return http.build();
	}
}