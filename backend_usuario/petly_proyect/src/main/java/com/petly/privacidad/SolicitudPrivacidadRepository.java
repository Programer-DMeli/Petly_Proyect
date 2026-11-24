package com.petly.privacidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudPrivacidadRepository extends JpaRepository<SolicitudPrivacidad, String> {
    Optional<SolicitudPrivacidad> findByOperacionIdAndUsuarioId(String operacionId, Long usuarioId);

    List<SolicitudPrivacidad> findTop100ByTipoAndEstadoInOrderByCreadoEnAsc(
            SolicitudPrivacidad.Tipo tipo,
            List<SolicitudPrivacidad.Estado> estados);
}
