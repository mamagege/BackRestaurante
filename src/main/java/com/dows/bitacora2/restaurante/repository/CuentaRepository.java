package com.dows.bitacora2.restaurante.repository;

import com.dows.bitacora2.restaurante.model.domain.EstadoCuenta;
import com.dows.bitacora2.restaurante.persistence.entity.CuentaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CuentaRepository extends JpaRepository<CuentaEntity, Long> {
    Optional<CuentaEntity> findFirstByIdMesaAndEstado(Long idMesa, EstadoCuenta estado);
    boolean existsByIdMesaAndEstado(Long idMesa, EstadoCuenta estado);
    List<CuentaEntity> findByIdMesaOrderByFechaAperturaDesc(Long idMesa);
    List<CuentaEntity> findByEstadoOrderByFechaAperturaDesc(EstadoCuenta estado);
    List<CuentaEntity> findByIdMesaAndEstadoOrderByFechaAperturaDesc(Long idMesa, EstadoCuenta estado);
}
