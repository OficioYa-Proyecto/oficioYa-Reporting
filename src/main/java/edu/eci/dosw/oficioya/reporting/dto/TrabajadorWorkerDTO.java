package edu.eci.dosw.oficioya.reporting.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Trabajador tal como lo lista Worker (Squad 3) en {@code GET /api/v1/workers}. Worker lista únicamente los activos:
 * los pausados los cuenta Reputation, que es quien los pausa.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrabajadorWorkerDTO(UUID userId, String status, OficioOfrecido primaryTrade,
                                  List<OficioOfrecido> secondaryTrades) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OficioOfrecido(BigDecimal approxRate, String rateUnit, Oficio trade) {

        String nombre() {
            return trade == null || trade.name() == null ? SIN_OFICIO : trade.name();
        }

        boolean esPorTrabajo() {
            return POR_TRABAJO.equals(rateUnit) && approxRate != null && approxRate.signum() > 0;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Oficio(String name) {
    }

    private static final String ACTIVO = "ACTIVE";
    private static final String SIN_OFICIO = "Sin oficio";
    /** Solo se usan las tarifas por trabajo: mezclar por hora, por día y por trabajo daría un número sin sentido. */
    private static final String POR_TRABAJO = "PER_JOB";
    private static final Comparator<ConteoDTO> DE_MAYOR_A_MENOR =
            Comparator.comparingLong(ConteoDTO::cantidad).reversed().thenComparing(ConteoDTO::etiqueta);

    /** Activos, activos por oficio principal, oficio de cada cuenta y tarifas "por trabajo" publicadas. */
    public static EstadisticasTrabajadoresDTO aEstadisticas(List<TrabajadorWorkerDTO> trabajadores) {
        List<TrabajadorWorkerDTO> activos = trabajadores.stream()
                .filter(Objects::nonNull)
                .filter(t -> t.status() == null || ACTIVO.equals(t.status()))
                .toList();
        List<ConteoDTO> porOficio = activos.stream()
                .collect(Collectors.groupingBy(TrabajadorWorkerDTO::oficioPrincipal, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new ConteoDTO(e.getKey(), e.getValue()))
                .sorted(DE_MAYOR_A_MENOR)
                .toList();
        Map<UUID, String> oficioPorCuenta = new HashMap<>();
        activos.stream().filter(t -> t.userId() != null)
                .forEach(t -> oficioPorCuenta.put(t.userId(), t.oficioPrincipal()));
        List<OficioOfrecido> porTrabajo = activos.stream()
                .flatMap(TrabajadorWorkerDTO::ofrecidos)
                .filter(OficioOfrecido::esPorTrabajo)
                .toList();
        Double promedio = porTrabajo.isEmpty() ? null
                : mediana(porTrabajo.stream().map(o -> o.approxRate().doubleValue()).toList());
        List<ConteoDTO> tarifaPorOficio = porTrabajo.stream()
                .collect(Collectors.groupingBy(OficioOfrecido::nombre,
                        Collectors.mapping(o -> o.approxRate().doubleValue(), Collectors.toList())))
                .entrySet().stream()
                .map(e -> new ConteoDTO(e.getKey(), Math.round(mediana(e.getValue()))))
                .sorted(DE_MAYOR_A_MENOR)
                .toList();
        return new EstadisticasTrabajadoresDTO(activos.size(), 0, 0, porOficio, oficioPorCuenta, promedio,
                tarifaPorOficio);
    }

    private String oficioPrincipal() {
        return primaryTrade == null ? SIN_OFICIO : primaryTrade.nombre();
    }

    private Stream<OficioOfrecido> ofrecidos() {
        return Stream.concat(Stream.ofNullable(primaryTrade),
                secondaryTrades == null ? Stream.empty() : secondaryTrades.stream().filter(Objects::nonNull));
    }

    /**
     * Valor del medio de las tarifas (con un número par, el promedio de los dos del medio). Se usa la mediana y no el
     * promedio para que una tarifa mal escrita (por ejemplo, millones por un trabajo pequeño) no mueva el resultado.
     */
    static double mediana(List<Double> valores) {
        List<Double> ordenados = valores.stream().sorted().toList();
        int medio = ordenados.size() / 2;
        double valor = ordenados.size() % 2 == 1 ? ordenados.get(medio)
                : (ordenados.get(medio - 1) + ordenados.get(medio)) / 2;
        return redondear(valor);
    }

    private static double redondear(double valor) {
        return BigDecimal.valueOf(valor).setScale(0, RoundingMode.HALF_UP).doubleValue();
    }
}
