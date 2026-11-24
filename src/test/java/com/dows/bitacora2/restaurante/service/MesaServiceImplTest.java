package com.dows.bitacora2.restaurante.service;

import com.dows.bitacora2.restaurante.exception.RecursoNoEncontradoException;
import com.dows.bitacora2.restaurante.exception.ConflictoException;
import com.dows.bitacora2.restaurante.mapper.MesaEntityMapper;
import com.dows.bitacora2.restaurante.model.domain.EstadoMesa;
import com.dows.bitacora2.restaurante.model.domain.Mesa;
import com.dows.bitacora2.restaurante.persistence.entity.MesaEntity;
import com.dows.bitacora2.restaurante.repository.MesaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MesaServiceImplTest {

    @Mock
    private MesaRepository mesaRepository;

    @Mock
    private MesaEntityMapper entityMapper;

    @InjectMocks
    private MesaServiceImpl mesaService;

    private Mesa mesa;
    private MesaEntity mesaEntity;

    @BeforeEach
    void setUp() {
        mesa = new Mesa();
        mesa.setId(1L);
        mesa.setNumero(5);
        mesa.setCapacidad(4);
        mesa.setEstado(EstadoMesa.DISPONIBLE);
        mesa.setCuentaAbierta(false);

        mesaEntity = new MesaEntity();
        mesaEntity.setId(1L);
    }

    @Test
    void obtenerPorId_MesaExiste_DebeRetornarMesa() {
        when(mesaRepository.findById(1L)).thenReturn(Optional.of(mesaEntity));
        when(entityMapper.toDomain(mesaEntity)).thenReturn(mesa);

        Mesa result = mesaService.obtenerPorId(1L);

        assertNotNull(result);
        assertEquals(5, result.getNumero());
    }

    @Test
    void obtenerPorId_MesaNoExiste_DebeLanzarExcepcion() {
        when(mesaRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> {
            mesaService.obtenerPorId(1L);
        });
    }

    @Test
    void crear_MesaValida_DebeGuardarYRetornar() {
        when(entityMapper.toEntity(mesa)).thenReturn(mesaEntity);
        when(mesaRepository.save(mesaEntity)).thenReturn(mesaEntity);
        when(entityMapper.toDomain(mesaEntity)).thenReturn(mesa);

        Mesa result = mesaService.crear(mesa);

        assertNotNull(result);
        assertEquals(EstadoMesa.DISPONIBLE, result.getEstado());
        verify(mesaRepository).save(any());
    }

    @Test
    void crear_NumeroDuplicado_DebeLanzarConflicto() {
        when(mesaRepository.existsByNumero(5)).thenReturn(true);

        assertThrows(ConflictoException.class, () -> mesaService.crear(mesa));
        verify(mesaRepository, never()).save(any());
    }

    @Test
    void actualizar_NumeroDeOtraMesa_DebeLanzarConflicto() {
        when(mesaRepository.findById(1L)).thenReturn(Optional.of(mesaEntity));
        when(mesaRepository.existsByNumeroAndIdNot(5, 1L)).thenReturn(true);

        assertThrows(ConflictoException.class, () -> mesaService.actualizar(1L, mesa));
        verify(mesaRepository, never()).save(any());
    }

    @Test
    void actualizar_ConservaSuPropioNumero_NoEsConflicto() {
        when(mesaRepository.findById(1L)).thenReturn(Optional.of(mesaEntity));
        when(mesaRepository.existsByNumeroAndIdNot(5, 1L)).thenReturn(false);
        when(mesaRepository.save(mesaEntity)).thenReturn(mesaEntity);
        when(entityMapper.toDomain(mesaEntity)).thenReturn(mesa);

        assertNotNull(mesaService.actualizar(1L, mesa));
    }

    @Test
    void abrirCuenta_MesaConCuentaAbierta_DebeLanzarConflicto() {
        mesaEntity.setCuentaAbierta(true);
        when(mesaRepository.findById(1L)).thenReturn(Optional.of(mesaEntity));

        assertThrows(ConflictoException.class, () -> mesaService.abrirCuenta(1L));
    }

    @Test
    void cerrarCuenta_MesaSinCuentaAbierta_DebeLanzarConflicto() {
        mesaEntity.setCuentaAbierta(false);
        when(mesaRepository.findById(1L)).thenReturn(Optional.of(mesaEntity));

        assertThrows(ConflictoException.class, () -> mesaService.cerrarCuenta(1L));
    }

    @Test
    void cambiarEstado_MesaLibreAReservada_Cambia() {
        when(mesaRepository.findById(1L)).thenReturn(Optional.of(mesaEntity));
        when(entityMapper.toDomain(mesaEntity)).thenReturn(mesa);
        when(mesaRepository.save(mesaEntity)).thenReturn(mesaEntity);

        mesaService.cambiarEstado(1L, " reservada ");

        assertEquals(EstadoMesa.RESERVADA, mesaEntity.getEstado());
    }

    @Test
    void cambiarEstado_MesaConCuentaAbierta_DebeLanzarConflictoYNoGuardar() {
        mesa.setCuentaAbierta(true);
        mesa.setEstado(EstadoMesa.OCUPADA);
        when(mesaRepository.findById(1L)).thenReturn(Optional.of(mesaEntity));
        when(entityMapper.toDomain(mesaEntity)).thenReturn(mesa);

        assertThrows(ConflictoException.class, () -> mesaService.cambiarEstado(1L, "DISPONIBLE"));
        verify(mesaRepository, never()).save(any());
    }

    @Test
    void cambiarEstado_OcupadaManual_DebeLanzarReglaDeNegocio() {
        when(mesaRepository.findById(1L)).thenReturn(Optional.of(mesaEntity));
        when(entityMapper.toDomain(mesaEntity)).thenReturn(mesa);

        assertThrows(com.dows.bitacora2.restaurante.exception.ReglaDeNegocioException.class,
                () -> mesaService.cambiarEstado(1L, "OCUPADA"));
        verify(mesaRepository, never()).save(any());
    }

    @Test
    void cambiarEstado_EstadoInexistente_DebeLanzarEstadoInvalidoSinConsultar() {
        assertThrows(com.dows.bitacora2.restaurante.exception.EstadoInvalidoException.class,
                () -> mesaService.cambiarEstado(1L, "VOLANDO"));
        verifyNoInteractions(mesaRepository);
    }
}
