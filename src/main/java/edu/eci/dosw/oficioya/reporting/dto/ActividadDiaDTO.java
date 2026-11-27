package edu.eci.dosw.oficioya.reporting.dto;

/**
 * Personas distintas con actividad un día según Reputation: tuvieron un servicio cumplido o escribieron una reseña.
 */
public record ActividadDiaDTO(String fecha, long cantidad) {
}
