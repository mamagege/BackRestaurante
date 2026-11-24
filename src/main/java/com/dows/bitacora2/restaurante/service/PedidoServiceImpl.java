package com.dows.bitacora2.restaurante.service;

import com.dows.bitacora2.restaurante.exception.EstadoInvalidoException;
import com.dows.bitacora2.restaurante.exception.RecursoNoEncontradoException;
import com.dows.bitacora2.restaurante.exception.ReglaDeNegocioException;
import com.dows.bitacora2.restaurante.exception.TransicionEstadoInvalidaException;
import com.dows.bitacora2.restaurante.mapper.PedidoEntityMapper;
import com.dows.bitacora2.restaurante.model.domain.Cuenta;
import com.dows.bitacora2.restaurante.model.domain.EstadoPedido;
import com.dows.bitacora2.restaurante.model.domain.ItemPedido;
import com.dows.bitacora2.restaurante.model.domain.Pedido;
import com.dows.bitacora2.restaurante.model.domain.PedidoEstadoCambiado;
import com.dows.bitacora2.restaurante.security.UsuarioActual;
import org.springframework.context.ApplicationEventPublisher;
import com.dows.bitacora2.restaurante.model.domain.Plato;
import com.dows.bitacora2.restaurante.persistence.entity.PedidoEntity;
import com.dows.bitacora2.restaurante.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



import com.dows.bitacora2.restaurante.model.domain.ResumenVentas;
import com.dows.bitacora2.restaurante.util.CalculoUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

@Service
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class PedidoServiceImpl implements IPedidoService {
    

    private final PedidoRepository pedidoRepository;
    private final PedidoEntityMapper entityMapper;
    private final IPlatoService platoService;
    private final IMesaService mesaService;
    private final ICuentaService cuentaService;
    private final ApplicationEventPublisher eventos;
    private final UsuarioActual usuarioActual;

    @Override
    @Transactional(readOnly = true)
    public List<Pedido> obtenerTodos() {
        log.info("Obteniendo todos los pedidos");
        return entityMapper.toDomainList(pedidoRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pedido> buscar(Collection<EstadoPedido> estados, Long idCuenta) {
        boolean filtrarEstados = estados != null && !estados.isEmpty();
        List<PedidoEntity> pedidos;
        if (idCuenta != null) {
            pedidos = pedidoRepository.findByIdCuentaOrderByTimestampAsc(idCuenta).stream()
                    .filter(p -> !filtrarEstados || estados.contains(p.getEstado()))
                    .toList();
        } else if (filtrarEstados) {
            pedidos = pedidoRepository.findByEstadoInOrderByTimestampAsc(estados);
        } else {
            pedidos = pedidoRepository.findAllByOrderByTimestampAsc();
        }
        return entityMapper.toDomainList(pedidos);
    }

    @Override
    @Transactional(readOnly = true)
    public Pedido obtenerPorId(Long id) {
        log.debug("Buscando con id={}", id);
        return pedidoRepository.findById(id)
                .map(entityMapper::toDomain)
                .orElseThrow(() -> {
                    log.error("Pedido con id={} no encontrad@ (o error de flujo)", id);
                    return new RecursoNoEncontradoException("Pedido", id);
                });
    }

    @Override
    @Transactional
    public Pedido crear(Pedido pedido) {
        mesaService.obtenerPorId(pedido.getIdMesa());
        Cuenta cuenta = cuentaService.obtenerAbiertaPorMesa(pedido.getIdMesa());

        validarYCompletarItems(pedido.getItems());
        pedido.setIdCuenta(cuenta.getId());
        pedido.setCreadoPor(usuarioActual.obtener());

        pedido.setEstado(EstadoPedido.RECIBIDO);
        PedidoEntity nuevo = pedidoRepository.save(entityMapper.toEntity(pedido));
        log.info("Pedido creado: id={}, mesa={}", nuevo.getId(), nuevo.getIdMesa());

        publicar(nuevo.getId(), null, EstadoPedido.RECIBIDO, "Pedido creado");

        return entityMapper.toDomain(nuevo);
    }

    @Override
    @Transactional
    public Pedido actualizar(Long id, Pedido nuevosDatos) {
        PedidoEntity entity = buscarEntity(id);
        if (entity.getEstado() != EstadoPedido.RECIBIDO) {
            throw new ReglaDeNegocioException("RN-04: El pedido solo puede modificarse en estado RECIBIDO.");
        }

        validarYCompletarItems(nuevosDatos.getItems());
        // Se reemplazan los ítems sobre la entidad gestionada: orphanRemoval borra los anteriores
        entity.getItems().clear();
        nuevosDatos.getItems().forEach(item -> entity.getItems().add(entityMapper.toItemEntity(item)));

        PedidoEntity guardado = pedidoRepository.save(entity);
        log.info("Pedido actualizado: id={}", id);
        return entityMapper.toDomain(guardado);
    }

    @Override
    @Transactional
    public Pedido cambiarEstado(Long id, String estadoStr) {
        EstadoPedido nuevoEstado = parsearEstado(estadoStr);
        PedidoEntity entity = buscarEntity(id);
        EstadoPedido estadoAnterior = entity.getEstado();
        if (!estadoAnterior.puedeTransicionarA(nuevoEstado)) {
            throw new TransicionEstadoInvalidaException("No se puede pasar de " + estadoAnterior + " a " + nuevoEstado);
        }

        entity.setEstado(nuevoEstado);
        PedidoEntity guardado = pedidoRepository.save(entity);
        log.info("Estado de pedido id={} cambiado de {} a {}", id, estadoAnterior, nuevoEstado);

        publicar(id, estadoAnterior, nuevoEstado, "Cambio de estado");

        return entityMapper.toDomain(guardado);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        PedidoEntity entity = buscarEntity(id);
        EstadoPedido estadoAnterior = entity.getEstado();
        if (!estadoAnterior.esCancelable()) {
            throw new ReglaDeNegocioException("El pedido no se puede cancelar porque ya está en preparación o listo.");
        }
        entity.setEstado(EstadoPedido.CANCELADO);
        pedidoRepository.save(entity);
        log.info("Pedido cancelado: id={}", id);

        publicar(id, estadoAnterior, EstadoPedido.CANCELADO, "Pedido cancelado");
    }

    /** Se registra en auditoría solo si la transacción confirma (ver AuditoriaPedidoListener). */
    private void publicar(Long pedidoId, EstadoPedido anterior, EstadoPedido nuevo, String descripcion) {
        eventos.publishEvent(new PedidoEstadoCambiado(pedidoId, anterior, nuevo, usuarioActual.obtener(),
                LocalDateTime.now(), descripcion));
    }

    private PedidoEntity buscarEntity(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido", id));
    }

    private static EstadoPedido parsearEstado(String estadoStr) {
        try {
            return EstadoPedido.valueOf(estadoStr.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new EstadoInvalidoException("Estado de pedido inválido: " + estadoStr);
        }
    }

    /** Asocia cada ítem a su plato: disponibilidad, personalización (RN-01/RN-02/pasta) y precio congelado (RN-03). */
    private void validarYCompletarItems(List<ItemPedido> items) {
        for (ItemPedido item : items) {
            Plato plato = platoService.obtenerPorId(item.getIdPlato());
            item.aplicarPlato(plato);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenVentas resumenDelDia(LocalDate fecha) {
        List<PedidoEntity> pedidos = pedidoRepository
                .findByTimestampGreaterThanEqualAndTimestampLessThan(fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay())
                .stream()
                .filter(p -> p.getEstado() != EstadoPedido.CANCELADO)
                .toList();

        Map<String, long[]> cantidades = new LinkedHashMap<>();
        Map<String, Double> ingresosPorPlato = new LinkedHashMap<>();
        Map<String, Long> pedidosPorUsuario = new LinkedHashMap<>();
        Map<String, Double> ingresosPorUsuario = new LinkedHashMap<>();
        double ingresoTotal = 0;

        for (PedidoEntity pedido : pedidos) {
            String usuario = pedido.getCreadoPor() != null ? pedido.getCreadoPor() : "desconocido";
            double totalPedido = 0;
            for (var item : pedido.getItems()) {
                double subtotal = item.getPrecioCongelado() * item.getCantidad();
                totalPedido += subtotal;
                cantidades.computeIfAbsent(item.getNombrePlato(), k -> new long[1])[0] += item.getCantidad();
                ingresosPorPlato.merge(item.getNombrePlato(), subtotal, Double::sum);
            }
            ingresoTotal += totalPedido;
            pedidosPorUsuario.merge(usuario, 1L, Long::sum);
            ingresosPorUsuario.merge(usuario, totalPedido, Double::sum);
        }

        List<ResumenVentas.VentaPlato> porPlato = cantidades.entrySet().stream()
                .map(e -> new ResumenVentas.VentaPlato(e.getKey(), e.getValue()[0],
                        CalculoUtils.redondear(ingresosPorPlato.get(e.getKey()))))
                .sorted(Comparator.comparingLong(ResumenVentas.VentaPlato::cantidad).reversed()
                        .thenComparing(ResumenVentas.VentaPlato::plato))
                .toList();
        List<ResumenVentas.VentaUsuario> porUsuario = pedidosPorUsuario.entrySet().stream()
                .map(e -> new ResumenVentas.VentaUsuario(e.getKey(), e.getValue(),
                        CalculoUtils.redondear(ingresosPorUsuario.get(e.getKey()))))
                .sorted(Comparator.comparingDouble(ResumenVentas.VentaUsuario::ingresos).reversed()
                        .thenComparing(ResumenVentas.VentaUsuario::usuario))
                .toList();

        return new ResumenVentas(fecha, pedidos.size(), CalculoUtils.redondear(ingresoTotal), porPlato, porUsuario);
    }
}