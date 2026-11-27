package edu.eci.dosw.oficioya.reporting.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import edu.eci.dosw.oficioya.reporting.client.OrquestadorClient;
import edu.eci.dosw.oficioya.reporting.domain.DatosDemanda;
import edu.eci.dosw.oficioya.reporting.domain.DatosPlataforma;
import edu.eci.dosw.oficioya.reporting.domain.Estadistica;
import edu.eci.dosw.oficioya.reporting.domain.FormatoExportacion;
import edu.eci.dosw.oficioya.reporting.domain.Indicador;
import edu.eci.dosw.oficioya.reporting.domain.PeriodoConsulta;
import edu.eci.dosw.oficioya.reporting.domain.PuntoEstadistico;
import edu.eci.dosw.oficioya.reporting.domain.Reporte;
import edu.eci.dosw.oficioya.reporting.domain.TipoEstadistica;
import edu.eci.dosw.oficioya.reporting.domain.TipoIndicador;
import edu.eci.dosw.oficioya.reporting.domain.TipoReporte;
import edu.eci.dosw.oficioya.reporting.domain.indicador.CalculadorIndicador;
import edu.eci.dosw.oficioya.reporting.dto.ArchivoExportado;
import edu.eci.dosw.oficioya.reporting.dto.ConteoDTO;
import edu.eci.dosw.oficioya.reporting.dto.EstadisticasReputacionDTO;
import edu.eci.dosw.oficioya.reporting.dto.EstadisticasServiciosDTO;
import edu.eci.dosw.oficioya.reporting.dto.EstadisticasTrabajadoresDTO;
import edu.eci.dosw.oficioya.reporting.dto.EstadisticasUsuariosDTO;
import edu.eci.dosw.oficioya.reporting.exception.IntegracionException;
import edu.eci.dosw.oficioya.reporting.service.exportacion.ExportadorReporte;
import edu.eci.dosw.oficioya.reporting.service.exportacion.ExportadorFactory;

/**
 * Casos de uso de Reporting: catálogo de reportes, generación, indicadores, estadísticas y exportación.
 * No guarda nada: cada consulta pide los datos agregados a los otros dominios a través del Orchestrator.
 * Si un dominio no responde, el reporte sale con los demás y lo indica en {@link Reporte#fuentesSinDatos()}.
 */
@Service
public class ReporteService {

    private static final Logger log = LoggerFactory.getLogger(ReporteService.class);

    static final String FUENTE_SERVICE = "Service";
    static final String FUENTE_WORKER = "Worker";
    static final String FUENTE_REPUTATION = "Reputation";
    static final String FUENTE_USERS = "Users";
    private static final int FUENTES = 4;

    private static final EstadisticasServiciosDTO SERVICIOS_SIN_DATOS =
            new EstadisticasServiciosDTO(Map.of(), List.of(), List.of());
    private static final EstadisticasTrabajadoresDTO TRABAJADORES_SIN_DATOS =
            new EstadisticasTrabajadoresDTO(0, 0, 0, List.of());
    private static final EstadisticasReputacionDTO REPUTACION_SIN_DATOS = new EstadisticasReputacionDTO(0, 0, 0, 0, 0);
    private static final EstadisticasUsuariosDTO USUARIOS_SIN_DATOS = new EstadisticasUsuariosDTO(0, 0, List.of());

    private final OrquestadorClient orquestador;
    private final Map<TipoIndicador, CalculadorIndicador> calculadores = new EnumMap<>(TipoIndicador.class);
    private final ExportadorFactory exportadores;
    private final Clock reloj;
    private final int diasPorDefecto;

    public ReporteService(OrquestadorClient orquestador, List<CalculadorIndicador> calculadores,
                          ExportadorFactory exportadores, Clock reloj,
                          @Value("${oficioya.reporting.dias-periodo-por-defecto:7}") int diasPorDefecto) {
        this.orquestador = orquestador;
        calculadores.forEach(c -> this.calculadores.put(c.tipo(), c));
        if (this.calculadores.size() != TipoIndicador.values().length) {
            throw new IllegalStateException("Falta el calculador de algún indicador");
        }
        this.exportadores = exportadores;
        this.reloj = reloj;
        this.diasPorDefecto = diasPorDefecto;
    }

    /** Periodo pedido o, si no se envían fechas, los últimos días configurados hasta hoy. */
    public PeriodoConsulta periodo(LocalDate desde, LocalDate hasta) {
        return PeriodoConsulta.de(desde, hasta, LocalDate.now(reloj), diasPorDefecto);
    }

    /** RF-01: reportes disponibles. */
    public List<TipoReporte> catalogo() {
        return Arrays.asList(TipoReporte.values());
    }

    /** RF-01 / RF-04: genera el reporte del tipo y periodo pedidos. */
    public Reporte generar(TipoReporte tipo, PeriodoConsulta periodo, String administrador) {
        Reporte reporte = construir(tipo, periodo);
        log.info("Reporte generado: tipo={} periodo={}..{} administrador={} indicadores={} estadisticas={}",
                tipo, periodo.desde(), periodo.hasta(), administrador, reporte.indicadores().size(),
                reporte.estadisticas().size());
        return reporte;
    }

    /** RF-02: todos los indicadores del periodo, comparados con el periodo anterior. */
    public List<Indicador> indicadores(PeriodoConsulta periodo, String administrador) {
        List<Indicador> indicadores = construir(TipoReporte.GENERAL, periodo).indicadores();
        log.info("Indicadores consultados: periodo={}..{} administrador={}", periodo.desde(), periodo.hasta(),
                administrador);
        return indicadores;
    }

    /** RF-03: todas las estadísticas agregadas del periodo. */
    public List<Estadistica> estadisticas(PeriodoConsulta periodo, String administrador) {
        Set<String> sinDatos = new LinkedHashSet<>();
        DatosPlataforma datos = datos(periodo, trabajadores(sinDatos), sinDatos);
        verificarQueAlgunDominioRespondio(sinDatos);
        List<Estadistica> estadisticas = Arrays.stream(TipoEstadistica.values()).map(datos::estadistica).toList();
        log.info("Estadísticas consultadas: periodo={}..{} administrador={}", periodo.desde(), periodo.hasta(),
                administrador);
        return estadisticas;
    }

    /**
     * RF-08: exporta el mismo reporte que se consulta (se genera con el mismo método, por eso son consistentes).
     */
    public ArchivoExportado exportar(TipoReporte tipo, PeriodoConsulta periodo, FormatoExportacion formato,
                                     String administrador) {
        ExportadorReporte exportador = exportadores.obtener(formato);
        Reporte reporte = construir(tipo, periodo);
        byte[] contenido = exportador.exportar(reporte);
        String nombre = "reporte-%s-%s_%s.%s".formatted(tipo.name().toLowerCase(), periodo.desde(), periodo.hasta(),
                exportador.extension());
        log.info("Reporte exportado: tipo={} formato={} periodo={}..{} administrador={} bytes={}", tipo, formato,
                periodo.desde(), periodo.hasta(), administrador, contenido.length);
        return new ArchivoExportado(nombre, exportador.tipoContenido(), contenido);
    }

    private Reporte construir(TipoReporte tipo, PeriodoConsulta periodo) {
        Set<String> sinDatos = new LinkedHashSet<>();
        EstadisticasTrabajadoresDTO trabajadores = trabajadores(sinDatos);
        DatosPlataforma actual = datos(periodo, trabajadores, sinDatos);
        DatosPlataforma anterior = datos(periodo.periodoAnterior(), trabajadores, sinDatos);
        verificarQueAlgunDominioRespondio(sinDatos);
        List<Indicador> indicadores = tipo.indicadores().stream()
                .map(t -> calculadores.get(t).calcular(actual, anterior))
                .toList();
        List<Estadistica> estadisticas = tipo.estadisticas().stream().map(actual::estadistica).toList();
        return new Reporte(tipo, periodo, LocalDateTime.now(reloj), indicadores, estadisticas, List.copyOf(sinDatos));
    }

    private EstadisticasTrabajadoresDTO trabajadores(Set<String> sinDatos) {
        return consultar(orquestador::estadisticasTrabajadores, TRABAJADORES_SIN_DATOS, FUENTE_WORKER, sinDatos);
    }

    /**
     * Pide los datos a un dominio. Si no responde, se sigue con datos vacíos y se anota el dominio: así el panel
     * muestra lo que sí está disponible en vez de fallar completo.
     */
    private static <T> T consultar(Supplier<T> consulta, T vacio, String fuente, Set<String> sinDatos) {
        try {
            return consulta.get();
        } catch (IntegracionException e) {
            if (sinDatos.add(fuente)) {
                log.warn("Reporte sin datos de {}: {}", fuente, e.getMessage());
            }
            return vacio;
        }
    }

    /** Si ningún dominio respondió no hay nada que mostrar: se informa como falla de integración. */
    private static void verificarQueAlgunDominioRespondio(Set<String> sinDatos) {
        if (sinDatos.size() == FUENTES) {
            throw new IntegracionException("Ningún dominio respondió a través del Orchestrator", null);
        }
    }

    /** Une lo que entregan Service, Worker, Reputation y Users en los datos del periodo. */
    private DatosPlataforma datos(PeriodoConsulta periodo, EstadisticasTrabajadoresDTO trabajadores,
                                  Set<String> sinDatos) {
        EstadisticasServiciosDTO servicios = consultar(() -> orquestador.estadisticasServicios(periodo),
                SERVICIOS_SIN_DATOS, FUENTE_SERVICE, sinDatos);
        EstadisticasReputacionDTO reputacion = consultar(() -> orquestador.estadisticasReputacion(periodo),
                REPUTACION_SIN_DATOS, FUENTE_REPUTATION, sinDatos);
        EstadisticasUsuariosDTO usuarios = consultar(() -> orquestador.estadisticasUsuarios(periodo),
                USUARIOS_SIN_DATOS, FUENTE_USERS, sinDatos);
        return new DatosPlataforma(periodo,
                servicios.solicitudesPorEstado() == null ? Map.of() : servicios.solicitudesPorEstado(),
                porDia(periodo, servicios.cumplidosPorDia()),
                puntos(servicios.cumplidosPorOficio()),
                trabajadores.activos(), trabajadores.pausadosVoluntarios(),
                trabajadores.pausadosPorReputacion() + reputacion.trabajadoresPausados(),
                puntos(trabajadores.activosPorOficio()),
                reputacion.calificacionPromedio(), reputacion.resenasReportadas(), reputacion.reportesPendientes(),
                reputacion.reportesResueltos(),
                new DatosDemanda(usuarios.cuentasActivas(), usuarios.cuentasInactivas(),
                        porDia(periodo, usuarios.activosPorDia()),
                        puntos(servicios.solicitudesPorOficio()), puntos(servicios.solicitudesPorZona()),
                        servicios.tarifaPromedio() == null ? 0 : servicios.tarifaPromedio(),
                        puntos(servicios.tarifaPromedioPorOficio()), puntos(servicios.solicitudesPorFranja())));
    }

    /** Un punto por cada día del periodo; los días sin datos quedan en 0 para que la gráfica no tenga huecos. */
    static List<PuntoEstadistico> porDia(PeriodoConsulta periodo, List<ConteoDTO> conteos) {
        Map<String, Long> porFecha = conteos == null ? Map.of()
                : conteos.stream().collect(Collectors.toMap(ConteoDTO::etiqueta, ConteoDTO::cantidad, Long::sum));
        return periodo.fechas().stream()
                .map(f -> new PuntoEstadistico(f.toString(), porFecha.getOrDefault(f.toString(), 0L)))
                .toList();
    }

    private static List<PuntoEstadistico> puntos(List<ConteoDTO> conteos) {
        if (conteos == null) {
            return List.of();
        }
        return conteos.stream().map(c -> new PuntoEstadistico(c.etiqueta(), c.cantidad())).toList();
    }
}
