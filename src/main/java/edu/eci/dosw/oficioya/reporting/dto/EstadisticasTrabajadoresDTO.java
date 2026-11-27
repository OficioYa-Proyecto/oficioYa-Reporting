package edu.eci.dosw.oficioya.reporting.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que Reporting saca de la lista de trabajadores activos de Worker (Squad 3).
 *
 * @param oficioPorCuenta           oficio principal de cada trabajador, por su cuenta de Users (para poner oficio a los
 *                                  servicios cumplidos que cuenta Reputation)
 * @param tarifaPromedioPorTrabajo  mediana de las tarifas publicadas "por trabajo" (COP); null si no hay ninguna
 * @param tarifaPorTrabajoPorOficio mediana de las tarifas publicadas "por trabajo" de cada oficio (COP)
 */
public record EstadisticasTrabajadoresDTO(
        long activos,
        long pausadosVoluntarios,
        long pausadosPorReputacion,
        List<ConteoDTO> activosPorOficio,
        Map<UUID, String> oficioPorCuenta,
        Double tarifaPromedioPorTrabajo,
        List<ConteoDTO> tarifaPorTrabajoPorOficio) {

    public EstadisticasTrabajadoresDTO {
        activosPorOficio = activosPorOficio == null ? List.of() : List.copyOf(activosPorOficio);
        oficioPorCuenta = oficioPorCuenta == null ? Map.of() : Map.copyOf(oficioPorCuenta);
        tarifaPorTrabajoPorOficio = tarifaPorTrabajoPorOficio == null ? List.of() : List.copyOf(tarifaPorTrabajoPorOficio);
    }

    /** Solo conteos (sin oficio por cuenta ni tarifas). */
    public EstadisticasTrabajadoresDTO(long activos, long pausadosVoluntarios, long pausadosPorReputacion,
                                       List<ConteoDTO> activosPorOficio) {
        this(activos, pausadosVoluntarios, pausadosPorReputacion, activosPorOficio, null, null, null);
    }
}
