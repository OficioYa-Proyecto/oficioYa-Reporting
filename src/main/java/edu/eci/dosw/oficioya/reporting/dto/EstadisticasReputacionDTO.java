package edu.eci.dosw.oficioya.reporting.dto;

import java.util.List;

/**
 * Lo que Reputation (Squad 5) entrega vía Orchestrator para un periodo
 * ({@code GET /api/v1/reputation/estadisticas}). {@code trabajadoresPausados} es el estado actual: Reputation es quien
 * pausa a un trabajador por su calificación y Worker no lista a los pausados.
 */
public record EstadisticasReputacionDTO(
        long totalResenas,
        double calificacionPromedio,
        long resenasReportadas,
        long reportesPendientes,
        long reportesResueltos,
        long trabajadoresPausados,
        List<CumplidosTrabajadorDTO> cumplidosPorTrabajador,
        List<ActividadDiaDTO> usuariosConActividadPorDia) {

    public EstadisticasReputacionDTO {
        cumplidosPorTrabajador = cumplidosPorTrabajador == null ? List.of() : List.copyOf(cumplidosPorTrabajador);
        usuariosConActividadPorDia = usuariosConActividadPorDia == null ? List.of()
                : List.copyOf(usuariosConActividadPorDia);
    }

    /** Sin la actividad por día. */
    public EstadisticasReputacionDTO(long totalResenas, double calificacionPromedio, long resenasReportadas,
                                     long reportesPendientes, long reportesResueltos, long trabajadoresPausados,
                                     List<CumplidosTrabajadorDTO> cumplidosPorTrabajador) {
        this(totalResenas, calificacionPromedio, resenasReportadas, reportesPendientes, reportesResueltos,
                trabajadoresPausados, cumplidosPorTrabajador, null);
    }

    /** Sin el conteo de pausados (versiones de Reputation anteriores lo omiten y llega en 0). */
    public EstadisticasReputacionDTO(long totalResenas, double calificacionPromedio, long resenasReportadas,
                                     long reportesPendientes, long reportesResueltos) {
        this(totalResenas, calificacionPromedio, resenasReportadas, reportesPendientes, reportesResueltos, 0, null,
                null);
    }
}
