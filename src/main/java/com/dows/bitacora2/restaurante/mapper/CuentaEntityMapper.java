package com.dows.bitacora2.restaurante.mapper;

import com.dows.bitacora2.restaurante.model.domain.Cuenta;
import com.dows.bitacora2.restaurante.persistence.entity.CuentaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CuentaEntityMapper {
    @org.mapstruct.Mapping(target = "version", ignore = true)
    CuentaEntity toEntity(Cuenta cuenta);
    Cuenta toDomain(CuentaEntity entity);
    List<Cuenta> toDomainList(List<CuentaEntity> entities);

    /** Copia el estado del dominio sobre la entidad gestionada (sin tocar identidad ni apertura). */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "idMesa", ignore = true)
    @Mapping(target = "fechaApertura", ignore = true)
    @Mapping(target = "version", ignore = true)
    void actualizarEntity(Cuenta cuenta, @MappingTarget CuentaEntity entity);
}
