package com.dows.bitacora2.restaurante.repository;

import com.dows.bitacora2.restaurante.persistence.entity.PlatoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatoRepository extends JpaRepository<PlatoEntity, Long> {
    List<PlatoEntity> findByDisponibleTrue();
    List<PlatoEntity> findByCategoriaIgnoreCase(String categoria);
    List<PlatoEntity> findByCategoriaIgnoreCaseAndDisponibleTrue(String categoria);
    Optional<PlatoEntity> findByIdAndDisponibleTrue(Long id);
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
