package edu.eci.dosw.oficioya.reporting.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import edu.eci.dosw.oficioya.reporting.domain.PeriodoConsulta;
import edu.eci.dosw.oficioya.reporting.dto.ConteoDTO;
import edu.eci.dosw.oficioya.reporting.dto.EstadisticasServiciosDTO;
import edu.eci.dosw.oficioya.reporting.dto.EstadisticasTrabajadoresDTO;
import edu.eci.dosw.oficioya.reporting.dto.EstadisticasUsuariosDTO;
import edu.eci.dosw.oficioya.reporting.exception.IntegracionException;

class OrquestadorClientTest {

    private static final String BASE = "http://orquestador";
    private static final PeriodoConsulta SEMANA =
            new PeriodoConsulta(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 10));
    private static final String CONSULTA = "?desde=2026-10-04&hasta=2026-10-10";
    private static final String CONSULTA_SERVICE = "?from=2026-10-04T00:00&to=2026-10-10T23:59:59";

    private MockRestServiceServer servidor;
    private OrquestadorRestClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new OrquestadorRestClient(builder, BASE, "/api/v1/services/reports",
                "/api/v1/workers", "/api/v1/reputation/estadisticas",
                "/api/v1/users/stats/accounts");
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void consultaLosCuatroDominiosPorElOrquestadorReenviandoElJwt() {
        Jwt jwt = Jwt.withTokenValue("token-admin").header("alg", "HS256").subject("admin-1").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        servidor.expect(requestTo(BASE + "/api/v1/services/reports" + CONSULTA_SERVICE))
                .andExpect(header("Authorization", "Bearer token-admin"))
                .andRespond(withSuccess("""
                        {"total": 1300, "byStatus": {"COMPLETED": 1248, "PENDING": 52},
                         "byNeighborhood": {"Chapinero": 700}, "byHour": {"9": 1000, "19": 300}}""",
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/api/v1/workers"))
                .andRespond(withSuccess("""
                        [{"id": "a", "status": "ACTIVE", "primaryTrade": {"trade": {"name": "Plomero"}}},
                         {"id": "b", "status": "ACTIVE", "primaryTrade": {"trade": {"name": "Pintor"}}},
                         {"id": "c", "status": "ACTIVE", "primaryTrade": {"trade": {"name": "Plomero"}}}]""",
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/api/v1/reputation/estadisticas" + CONSULTA))
                .andRespond(withSuccess("""
                        {"totalResenas": 500, "calificacionPromedio": 4.6, "resenasReportadas": 7,
                         "reportesPendientes": 5, "reportesResueltos": 2, "trabajadoresPausados": 3}""",
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/api/v1/users/stats/accounts?from=2000-01-01&to=2026-10-10"))
                .andExpect(header("Authorization", "Bearer token-admin"))
                .andRespond(withSuccess("""
                        {"registered": {"total": 1716, "trabajador": 500, "contratante": 1300},
                         "deactivated": {"total": 236, "trabajador": 40, "contratante": 200}}""",
                        MediaType.APPLICATION_JSON));

        EstadisticasServiciosDTO servicios = cliente.estadisticasServicios(SEMANA);
        EstadisticasTrabajadoresDTO trabajadores = cliente.estadisticasTrabajadores();

        assertThat(servicios.solicitudesPorEstado()).containsEntry("CUMPLIDA", 1248L).containsEntry("PENDIENTE", 52L);
        assertThat(servicios.solicitudesPorZona()).containsExactly(new ConteoDTO("Chapinero", 700));
        assertThat(servicios.solicitudesPorFranja()).extracting(ConteoDTO::cantidad).containsExactly(0L, 1000L, 0L, 300L);
        assertThat(trabajadores.activos()).isEqualTo(3);
        assertThat(trabajadores.activosPorOficio())
                .containsExactly(new ConteoDTO("Plomero", 2), new ConteoDTO("Pintor", 1));
        var reputacion = cliente.estadisticasReputacion(SEMANA);
        assertThat(reputacion.reportesPendientes()).isEqualTo(5);
        assertThat(reputacion.trabajadoresPausados()).isEqualTo(3);
        EstadisticasUsuariosDTO usuarios = cliente.estadisticasUsuarios(SEMANA);
        assertThat(usuarios.cuentasActivas()).isEqualTo(1480);
        assertThat(usuarios.cuentasInactivas()).isEqualTo(236);
        assertThat(usuarios.activosPorDia()).isEmpty();
        servidor.verify();
    }

    @Test
    void erroresORespuestasVaciasSonFallasDeIntegracion() {
        servidor.expect(requestTo(BASE + "/api/v1/services/reports" + CONSULTA_SERVICE)).andRespond(withServerError());
        servidor.expect(requestTo(BASE + "/api/v1/workers")).andRespond(withSuccess());
        assertThatThrownBy(() -> cliente.estadisticasServicios(SEMANA)).isInstanceOf(IntegracionException.class);
        assertThatThrownBy(() -> cliente.estadisticasTrabajadores())
                .isInstanceOf(IntegracionException.class)
                .hasMessageContaining("Worker");
    }

    @Test
    void elClienteSimuladoEntregaDatosRepetibles() {
        OrquestadorClientSimulado simulado = new OrquestadorClientSimulado();
        EstadisticasServiciosDTO servicios = simulado.estadisticasServicios(SEMANA);
        assertThat(servicios.cumplidosPorDia()).hasSize(7);
        assertThat(servicios).isEqualTo(simulado.estadisticasServicios(SEMANA));
        assertThat(simulado.estadisticasTrabajadores().pausadosPorReputacion()).isEqualTo(6);
        assertThat(simulado.estadisticasReputacion(SEMANA).resenasReportadas()).isEqualTo(7);
        assertThat(OrquestadorClientSimulado.cumplidosDelDia(LocalDate.of(2026, 10, 4))).isBetween(140L, 145L);
        assertThat(servicios.solicitudesPorOficio()).hasSize(6);
        assertThat(servicios.solicitudesPorZona()).hasSize(6);
        assertThat(servicios.tarifaPromedio()).isPositive();
        assertThat(simulado.estadisticasUsuarios(SEMANA).activosPorDia()).hasSize(7);
        assertThat(OrquestadorClientSimulado.activosDelDia(LocalDate.of(2026, 10, 4))).isBetween(480L, 530L);
    }
}
