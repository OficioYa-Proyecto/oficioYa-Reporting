package edu.eci.dosw.oficioya.reporting.domain.indicador;

import org.springframework.stereotype.Component;

import edu.eci.dosw.oficioya.reporting.domain.DatosPlataforma;
import edu.eci.dosw.oficioya.reporting.domain.Indicador;
import edu.eci.dosw.oficioya.reporting.domain.TipoIndicador;

/**
 * Promedio diario de usuarios activos en el periodo, comparado con el periodo anterior.
 */
@Component
public class CalculadorPromedioUsuariosActivos implements CalculadorIndicador {

    @Override
    public TipoIndicador tipo() {
        return TipoIndicador.PROMEDIO_USUARIOS_ACTIVOS;
    }

    @Override
    public Indicador calcular(DatosPlataforma actual, DatosPlataforma anterior) {
        String detalle = actual.demanda().usuariosActivosPorDia().stream().allMatch(p -> p.valor() == 0)
                ? "Sin servicios cumplidos ni reseñas en el periodo"
                : "Promedio diario de personas con un servicio cumplido o una reseña";
        return new Indicador(tipo(), actual.demanda().promedioUsuariosActivos(),
                anterior.demanda().promedioUsuariosActivos(), "usuarios por día", detalle);
    }
}
