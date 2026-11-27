package edu.eci.dosw.oficioya.reporting.dto;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Trabajador tal como lo lista Worker (Squad 3) en {@code GET /api/v1/workers}. Solo se leen el estado y el oficio
 * principal. Worker lista únicamente los activos: los pausados los cuenta Reputation, que es quien los pausa.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrabajadorWorkerDTO(String status, OficioDelTrabajador primaryTrade) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OficioDelTrabajador(Oficio trade) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Oficio(String name) {
    }

    private static final String ACTIVO = "ACTIVE";
    private static final String SIN_OFICIO = "Sin oficio";

    /** Activos y activos por oficio principal (de mayor a menor). Los pausados no llegan en esta lista. */
    public static EstadisticasTrabajadoresDTO aEstadisticas(List<TrabajadorWorkerDTO> trabajadores) {
        List<TrabajadorWorkerDTO> activos = trabajadores.stream()
                .filter(Objects::nonNull)
                .filter(t -> t.status() == null || ACTIVO.equals(t.status()))
                .toList();
        Map<String, Long> porOficio = activos.stream()
                .collect(Collectors.groupingBy(TrabajadorWorkerDTO::nombreOficio, Collectors.counting()));
        List<ConteoDTO> conteos = porOficio.entrySet().stream()
                .map(e -> new ConteoDTO(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(ConteoDTO::cantidad).reversed()
                        .thenComparing(ConteoDTO::etiqueta, Comparator.comparing(Function.identity())))
                .toList();
        return new EstadisticasTrabajadoresDTO(activos.size(), 0, 0, conteos);
    }

    private String nombreOficio() {
        return primaryTrade == null || primaryTrade.trade() == null || primaryTrade.trade().name() == null
                ? SIN_OFICIO : primaryTrade.trade().name();
    }
}
