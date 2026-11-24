package com.dows.bitacora2.restaurante.service;

import com.dows.bitacora2.restaurante.exception.EstadoInvalidoException;
import com.dows.bitacora2.restaurante.exception.RecursoNoEncontradoException;
import com.dows.bitacora2.restaurante.exception.ConflictoException;
import com.dows.bitacora2.restaurante.mapper.MesaEntityMapper;
import com.dows.bitacora2.restaurante.model.domain.EstadoMesa;
import com.dows.bitacora2.restaurante.model.domain.Mesa;
import com.dows.bitacora2.restaurante.persistence.entity.MesaEntity;
import com.dows.bitacora2.restaurante.repository.MesaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



import java.util.List;
import java.util.Locale;

@Service
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class MesaServiceImpl implements IMesaService {
    

    private final MesaRepository mesaRepository;
    private final MesaEntityMapper entityMapper;

    @Override
    public List<Mesa> obtenerTodas() {
        log.info("Obteniendo todas las mesas");
        return entityMapper.toDomainList(mesaRepository.findAll());
    }

    @Override
    public List<Mesa> obtenerDisponibles() {
        return mesaRepository.findAll().stream()
                .map(entityMapper::toDomain)
                .filter(Mesa::estaDisponible)
                .toList();
    }

    @Override
    public Mesa obtenerPorId(Long id) {
        log.debug("Buscando con id={}", id);
        return mesaRepository.findById(id)
                .map(entityMapper::toDomain)
                .orElseThrow(() -> {
                    log.error("Mesa con id={} no encontrad@ (o error de flujo)", id);
                    return new RecursoNoEncontradoException("Mesa", id);
                });
    }

    @Override
    @Transactional
    public Mesa crear(Mesa mesa) {
        if (mesaRepository.existsByNumero(mesa.getNumero())) {
            throw new ConflictoException("Ya existe una mesa con el número " + mesa.getNumero());
        }
        mesa.setEstado(EstadoMesa.DISPONIBLE);
        mesa.setCuentaAbierta(false);
        MesaEntity nueva = mesaRepository.save(entityMapper.toEntity(mesa));
        log.info("Mesa creada: id={}, numero={}", nueva.getId(), nueva.getNumero());
        return entityMapper.toDomain(nueva);
    }

    @Override
    @Transactional
    public Mesa actualizar(Long id, Mesa mesaModificada) {
        MesaEntity existente = mesaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mesa", id));
        
        if (mesaRepository.existsByNumeroAndIdNot(mesaModificada.getNumero(), id)) {
            throw new ConflictoException("Ya existe otra mesa con el número " + mesaModificada.getNumero());
        }

        existente.setNumero(mesaModificada.getNumero());
        existente.setCapacidad(mesaModificada.getCapacidad());
        log.info("Mesa actualizada: id={}", id);
        return entityMapper.toDomain(mesaRepository.save(existente));
    }

    @Override
    @Transactional
    public Mesa cambiarEstado(Long id, String estadoStr) {
        EstadoMesa nuevoEstado;
        try {
            nuevoEstado = EstadoMesa.valueOf(estadoStr.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new EstadoInvalidoException("Estado de mesa inválido: " + estadoStr);
        }
        MesaEntity entity = mesaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mesa", id));
        Mesa mesa = entityMapper.toDomain(entity);
        mesa.cambiarEstadoManual(nuevoEstado);
        entity.setEstado(mesa.getEstado());
        log.info("Estado de mesa id={} cambiado a {}", id, nuevoEstado);
        return entityMapper.toDomain(mesaRepository.save(entity));
    }

    @Override
    @Transactional
    public Mesa abrirCuenta(Long id) {
        MesaEntity mesa = mesaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mesa", id));
        if (Boolean.TRUE.equals(mesa.getCuentaAbierta())) {
            throw new ConflictoException("RN-06: La mesa ya tiene una cuenta abierta. Solo puede haber una cuenta abierta a la vez.");
        }
        mesa.setCuentaAbierta(true);
        mesa.setEstado(EstadoMesa.OCUPADA);
        log.info("Cuenta abierta para mesa id={}", id);
        return entityMapper.toDomain(mesaRepository.save(mesa));
    }

    @Override
    @Transactional
    public Mesa cerrarCuenta(Long id) {
        MesaEntity mesa = mesaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mesa", id));
        if (!Boolean.TRUE.equals(mesa.getCuentaAbierta())) {
            throw new ConflictoException("La mesa no tiene una cuenta abierta para cerrar.");
        }
        mesa.setCuentaAbierta(false);
        mesa.setEstado(EstadoMesa.DISPONIBLE);
        log.info("Cuenta cerrada para mesa id={}", id);
        return entityMapper.toDomain(mesaRepository.save(mesa));
    }
}

