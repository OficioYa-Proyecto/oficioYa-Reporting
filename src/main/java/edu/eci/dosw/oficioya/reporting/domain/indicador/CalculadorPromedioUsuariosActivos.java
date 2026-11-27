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
        // Users todavía no entrega la actividad por día: se dice en vez de mostrar un 0 que parece real.
        String detalle = actual.demanda().usuariosActivosPorDia().stream().allMatch(p -> p.valor() == 0)
                ? "Users aún no entrega la actividad diaria" : "Promedio diario en el periodo";
        return new Indicador(tipo(), actual.demanda().promedioUsuariosActivos(),
                anterior.demanda().promedioUsuariosActivos(), "usuarios por día", detalle);
    }
}
