package com.petly.autorizacion;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a autorizaciones de consulta (US-18). Sin logica de negocio aqui.
 */
public interface AutorizacionRepository extends JpaRepository<Autorizacion, Long> {

    Optional<Autorizacion> findByAdoptanteIdAndAlbergueId(Long adoptanteId, Long albergueId);

    List<Autorizacion> findByAdoptanteIdOrderByAlbergueIdAsc(Long adoptanteId);

    boolean existsByAdoptanteIdAndAlbergueIdAndEstado(
            Long adoptanteId, Long albergueId, EstadoAutorizacion estado);
}
