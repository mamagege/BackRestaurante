package com.dows.bitacora2.restaurante.controller;

import com.dows.bitacora2.restaurante.controller.docs.MenuApi;
import com.dows.bitacora2.restaurante.mapper.PlatoMapper;
import com.dows.bitacora2.restaurante.model.dto.response.PlatoResponseDTO;
import com.dows.bitacora2.restaurante.service.IPlatoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/menu")
@lombok.RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class MenuController implements MenuApi {

    private final IPlatoService platoService;
    private final PlatoMapper   platoMapper;

    @Override
    @GetMapping
    public ResponseEntity<List<PlatoResponseDTO>> verCarta() {
        return ResponseEntity.ok(platoMapper.toResponseList(platoService.obtenerDisponibles()));
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<PlatoResponseDTO> verDetalle(@PathVariable Long id) {
        return ResponseEntity.ok(platoMapper.toResponse(platoService.obtenerDisponiblePorId(id)));
    }

    @Override
    @GetMapping("/categoria/{categoria}")
    public ResponseEntity<List<PlatoResponseDTO>> porCategoria(@PathVariable String categoria) {
        return ResponseEntity.ok(platoMapper.toResponseList(platoService.obtenerDisponiblesPorCategoria(categoria)));
    }
}
