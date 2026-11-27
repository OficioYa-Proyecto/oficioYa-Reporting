package edu.eci.dosw.oficioya.reporting.dto;

import java.util.UUID;

/** Servicios cumplidos de un trabajador en el periodo, según Reputation (que recibe los avisos de Service). */
public record CumplidosTrabajadorDTO(UUID trabajadorId, long cantidad) {
}
