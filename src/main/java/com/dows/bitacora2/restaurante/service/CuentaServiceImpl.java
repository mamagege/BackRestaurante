package com.dows.bitacora2.restaurante.service;

import com.dows.bitacora2.restaurante.exception.ConflictoException;
import com.dows.bitacora2.restaurante.exception.RecursoNoEncontradoException;
import com.dows.bitacora2.restaurante.exception.ReglaDeNegocioException;
import com.dows.bitacora2.restaurante.mapper.CuentaEntityMapper;
import com.dows.bitacora2.restaurante.model.domain.Cuenta;
import com.dows.bitacora2.restaurante.model.domain.EstadoCuenta;
import com.dows.bitacora2.restaurante.model.domain.EstadoPedido;
import com.dows.bitacora2.restaurante.model.domain.MetodoPago;
import com.dows.bitacora2.restaurante.persistence.entity.CuentaEntity;
import com.dows.bitacora2.restaurante.persistence.entity.PedidoEntity;
import com.dows.bitacora2.restaurante.repository.CuentaRepository;
import com.dows.bitacora2.restaurante.repository.PedidoRepository;
import com.dows.bitacora2.restaurante.util.CalculoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ciclo de vida de la cuenta de una mesa. Los totales y la regla "todo entregado" se
 * calculan SOLO con los pedidos de la cuenta (no con todo el historial de la mesa).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CuentaServiceImpl implements ICuentaService {

    private final CuentaRepository cuentaRepository;
    private final CuentaEntityMapper entityMapper;
    private final PedidoRepository pedidoRepository;
    private final IMesaService mesaService;
    private final IPasarelaPago pasarelaPago;

    @Override
    @Transactional(readOnly = true)
    public List<Cuenta> obtenerTodas() {
        return entityMapper.toDomainList(cuentaRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Cuenta> buscar(Long idMesa, EstadoCuenta estado) {
        List<CuentaEntity> cuentas;
        if (idMesa != null && estado != null) {
            cuentas = cuentaRepository.findByIdMesaAndEstadoOrderByFechaAperturaDesc(idMesa, estado);
        } else if (idMesa != null) {
            cuentas = cuentaRepository.findByIdMesaOrderByFechaAperturaDesc(idMesa);
        } else if (estado != null) {
            cuentas = cuentaRepository.findByEstadoOrderByFechaAperturaDesc(estado);
        } else {
            cuentas = cuentaRepository.findAll();
        }
        return entityMapper.toDomainList(cuentas);
    }

    @Override
    @Transactional(readOnly = true)
    public Cuenta obtenerPorId(Long id) {
        return cuentaRepository.findById(id)
                .map(entityMapper::toDomain)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta", id));
    }

    @Override
    @Transactional(readOnly = true)
    public Cuenta obtenerAbiertaPorMesa(Long idMesa) {
        return cuentaRepository.findFirstByIdMesaAndEstado(idMesa, EstadoCuenta.ABIERTA)
                .map(entityMapper::toDomain)
                .orElseThrow(() -> new ReglaDeNegocioException("La mesa con id=" + idMesa
                        + " no tiene una cuenta abierta. Abra una cuenta antes de registrar pedidos."));
    }

    @Override
    @Transactional
    public Cuenta crear(Cuenta cuenta) {
        mesaService.obtenerPorId(cuenta.getIdMesa());
        if (cuentaRepository.existsByIdMesaAndEstado(cuenta.getIdMesa(), EstadoCuenta.ABIERTA)) {
            throw new ConflictoException("RN-06: La mesa ya tiene una cuenta abierta. "
                    + "Solo puede haber una cuenta abierta a la vez.");
        }
        mesaService.abrirCuenta(cuenta.getIdMesa());
        cuenta.setEstado(EstadoCuenta.ABIERTA);
        cuenta.setTotal(0.0);
        CuentaEntity nueva = cuentaRepository.save(entityMapper.toEntity(cuenta));
        log.info("Cuenta id={} abierta para la mesa id={}", nueva.getId(), nueva.getIdMesa());
        return entityMapper.toDomain(nueva);
    }

    @Override
    @Transactional
    public Cuenta actualizarTotal(Long id) {
        CuentaEntity entity = buscarEntity(id);
        Cuenta cuenta = entityMapper.toDomain(entity);
        if (cuenta.getEstado() == EstadoCuenta.CERRADA) {
            throw new ReglaDeNegocioException("La cuenta ya está pagada y no se puede actualizar.");
        }
        cuenta.setTotal(calcularTotal(pedidosDe(id)));
        entityMapper.actualizarEntity(cuenta, entity);
        log.info("Total de la cuenta id={} actualizado a {}", id, cuenta.getTotal());
        return entityMapper.toDomain(cuentaRepository.save(entity));
    }

    @Override
    @Transactional
    public Cuenta pagar(Long id, MetodoPago metodo, Double montoRecibido) {
        CuentaEntity entity = buscarEntity(id);
        Cuenta cuenta = entityMapper.toDomain(entity);
        if (!cuenta.estaAbierta()) {
            throw new ReglaDeNegocioException("La cuenta ya se encuentra pagada.");
        }

        List<PedidoEntity> pedidos = pedidosDe(id);
        boolean hayPedidosPendientes = pedidos.stream()
                .anyMatch(p -> p.getEstado() != EstadoPedido.ENTREGADO && p.getEstado() != EstadoPedido.CANCELADO);
        if (hayPedidosPendientes) {
            throw new ReglaDeNegocioException(
                    "No se puede pagar la cuenta. Hay pedidos aún en preparación o sin entregar.");
        }

        cuenta.setTotal(calcularTotal(pedidos));
        cuenta.validarPago(metodo, montoRecibido);

        String referencia = cobrar(cuenta, metodo);
        cuenta.registrarPago(metodo, montoRecibido, referencia, LocalDateTime.now());
        mesaService.cerrarCuenta(cuenta.getIdMesa());
        entityMapper.actualizarEntity(cuenta, entity);
        log.info("Cuenta id={} pagada con {}: total={}, recibido={}, cambio={}",
                id, metodo, cuenta.getTotal(), cuenta.getMontoRecibido(), cuenta.getCambio());
        return entityMapper.toDomain(cuentaRepository.save(entity));
    }

    /**
     * El efectivo se recibe en caja; tarjeta y transferencia pasan por la pasarela externa.
     * Si la pasarela rechaza, la cuenta sigue abierta y la mesa ocupada (RN-07).
     */
    private String cobrar(Cuenta cuenta, MetodoPago metodo) {
        if (metodo == MetodoPago.EFECTIVO || cuenta.calcularTotal() == 0.0) {
            return null;
        }
        IPasarelaPago.ResultadoPago resultado = pasarelaPago.procesar(cuenta.getId(), metodo, cuenta.calcularTotal());
        if (!resultado.aprobado()) {
            log.warn("Pago rechazado para la cuenta id={}: {}", cuenta.getId(), resultado.motivoRechazo());
            throw new ReglaDeNegocioException("Pago rechazado por la pasarela: " + resultado.motivoRechazo());
        }
        return resultado.referencia();
    }

    private CuentaEntity buscarEntity(Long id) {
        return cuentaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta", id));
    }

    private List<PedidoEntity> pedidosDe(Long idCuenta) {
        return pedidoRepository.findByIdCuenta(idCuenta);
    }

    private static double calcularTotal(List<PedidoEntity> pedidos) {
        return CalculoUtils.redondear(pedidos.stream()
                .filter(p -> p.getEstado() != EstadoPedido.CANCELADO)
                .flatMap(p -> p.getItems().stream())
                .mapToDouble(item -> item.getPrecioCongelado() * item.getCantidad())
                .sum());
    }
}
