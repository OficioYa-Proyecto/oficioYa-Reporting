package edu.eci.dosw.oficioya.reporting.client;

import java.time.LocalTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import edu.eci.dosw.oficioya.reporting.domain.PeriodoConsulta;
import edu.eci.dosw.oficioya.reporting.dto.CuentasUsersDTO;
import edu.eci.dosw.oficioya.reporting.dto.EstadisticasReputacionDTO;
import edu.eci.dosw.oficioya.reporting.dto.EstadisticasServiciosDTO;
import edu.eci.dosw.oficioya.reporting.dto.EstadisticasTrabajadoresDTO;
import edu.eci.dosw.oficioya.reporting.dto.EstadisticasUsuariosDTO;
import edu.eci.dosw.oficioya.reporting.dto.ReporteServiceDTO;
import edu.eci.dosw.oficioya.reporting.dto.TrabajadorWorkerDTO;
import edu.eci.dosw.oficioya.reporting.exception.IntegracionException;

/**
 * Implementación REST de {@link OrquestadorClient}. Las rutas son configurables y confirmadas por Squad 1. De Worker se
 * usa su lista de trabajadores activos, porque no tiene una ruta de estadísticas.
 */
@Component
@Profile("!demo")
public class OrquestadorRestClient implements OrquestadorClient {

    private final RestClient restClient;
    private final String rutaServicios;
    private final String rutaTrabajadores;
    private final String rutaReputacion;
    private final String rutaUsuarios;

    public OrquestadorRestClient(RestClient.Builder builder,
                                 @Value("${oficioya.orquestador.base-url}") String baseUrl,
                                 @Value("${oficioya.orquestador.rutas.estadisticas-servicios}") String rutaServicios,
                                 @Value("${oficioya.orquestador.rutas.estadisticas-trabajadores}") String rutaTrabajadores,
                                 @Value("${oficioya.orquestador.rutas.estadisticas-reputacion}") String rutaReputacion,
                                 @Value("${oficioya.orquestador.rutas.estadisticas-usuarios}") String rutaUsuarios) {
        this.restClient = builder.baseUrl(baseUrl).requestInterceptor(new PropagacionTokenInterceptor()).build();
        this.rutaServicios = rutaServicios;
        this.rutaTrabajadores = rutaTrabajadores;
        this.rutaReputacion = rutaReputacion;
        this.rutaUsuarios = rutaUsuarios;
    }

    @Override
    public EstadisticasServiciosDTO estadisticasServicios(PeriodoConsulta periodo) {
        // Formato real de Service: from/to con fecha y hora; se traduce con ReporteServiceDTO.
        try {
            ReporteServiceDTO reporte = restClient.get()
                    .uri(uri -> uri.path(rutaServicios)
                            .queryParam("from", periodo.desde().atStartOfDay())
                            .queryParam("to", periodo.hasta().atTime(LocalTime.MAX.withNano(0)))
                            .build())
                    .retrieve()
                    .body(ReporteServiceDTO.class);
            if (reporte == null) {
                throw new IntegracionException("Service no devolvió datos", null);
            }
            return reporte.aEstadisticas();
        } catch (RestClientException e) {
            throw new IntegracionException("No fue posible consultar los datos de Service", e);
        }
    }

    @Override
    public EstadisticasTrabajadoresDTO estadisticasTrabajadores() {
        // Formato real de Worker: lista de trabajadores activos; se cuentan aquí.
        try {
            List<TrabajadorWorkerDTO> trabajadores = restClient.get()
                    .uri(uri -> uri.path(rutaTrabajadores).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<TrabajadorWorkerDTO>>() { });
            if (trabajadores == null) {
                throw new IntegracionException("Worker respondió sin datos", null);
            }
            return TrabajadorWorkerDTO.aEstadisticas(trabajadores);
        } catch (RestClientException e) {
            throw new IntegracionException("No fue posible consultar los trabajadores de Worker", e);
        }
    }

    @Override
    public EstadisticasReputacionDTO estadisticasReputacion(PeriodoConsulta periodo) {
        return consultar(rutaReputacion, periodo, EstadisticasReputacionDTO.class, "Reputation");
    }

    @Override
    public EstadisticasUsuariosDTO estadisticasUsuarios(PeriodoConsulta periodo) {
        // Formato real de Users (RF-12): from/to en AAAA-MM-DD; se traduce con CuentasUsersDTO.
        try {
            CuentasUsersDTO cuentas = restClient.get()
                    .uri(uri -> uri.path(rutaUsuarios)
                            .queryParam("from", CuentasUsersDTO.DESDE_EL_INICIO)
                            .queryParam("to", periodo.hasta())
                            .build())
                    .retrieve()
                    .body(CuentasUsersDTO.class);
            if (cuentas == null) {
                throw new IntegracionException("Users no devolvió datos", null);
            }
            return cuentas.aEstadisticas();
        } catch (RestClientException e) {
            throw new IntegracionException("No fue posible consultar las estadísticas de Users", e);
        }
    }

    private <T> T consultar(String ruta, PeriodoConsulta periodo, Class<T> tipo, String dominio) {
        try {
            T respuesta = restClient.get()
                    .uri(uri -> {
                        uri.path(ruta);
                        if (periodo != null) {
                            uri.queryParam("desde", periodo.desde()).queryParam("hasta", periodo.hasta());
                        }
                        return uri.build();
                    })
                    .retrieve()
                    .body(tipo);
            if (respuesta == null) {
                throw new IntegracionException(dominio + " respondió sin datos", null);
            }
            return respuesta;
        } catch (RestClientException e) {
            throw new IntegracionException("No fue posible consultar las estadísticas de " + dominio, e);
        }
    }
}
