package com.dows.bitacora2.restaurante.mapper;

import com.dows.bitacora2.restaurante.model.domain.Mesa;
import com.dows.bitacora2.restaurante.persistence.entity.MesaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MesaEntityMapper {
    @org.mapstruct.Mapping(target = "version", ignore = true)
    MesaEntity toEntity(Mesa mesa);
    Mesa toDomain(MesaEntity entity);
    List<Mesa> toDomainList(List<MesaEntity> entities);
}
