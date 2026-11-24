package com.dows.bitacora2.restaurante.repository;

import com.dows.bitacora2.restaurante.persistence.entity.PedidoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dows.bitacora2.restaurante.model.domain.EstadoPedido;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface PedidoRepository extends JpaRepository<PedidoEntity, Long> {
    List<PedidoEntity> findByIdCuenta(Long idCuenta);
    List<PedidoEntity> findByIdCuentaOrderByTimestampAsc(Long idCuenta);
    List<PedidoEntity> findByEstadoInOrderByTimestampAsc(Collection<EstadoPedido> estados);
    List<PedidoEntity> findAllByOrderByTimestampAsc();
    List<PedidoEntity> findByTimestampGreaterThanEqualAndTimestampLessThan(LocalDateTime desde, LocalDateTime hasta);
}
