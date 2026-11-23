package com.biblio.virtual.controller;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.*;

import com.biblio.virtual.dto.DashboardResponseDTO;
import com.biblio.virtual.service.IDashboardService;

import java.io.ByteArrayInputStream;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/dashboard")
@CrossOrigin(origins = "*")
// Fix Broken Access Control (APF2): antes cualquier usuario autenticado
// (ROLE_USER) podia leer
// las metricas de toda la biblioteca y exportarlas. Solo ADMIN, igual que la
// ruta del frontend
// (adminGuard) y que el menu "Dashboard" del navbar. Aplica a /dashboard,
// /filtrar y /exportar.
@PreAuthorize("hasAuthority(@roles.ADMIN())")
public class DashboardController {

  private final IDashboardService dashboardService;

  public DashboardController(IDashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  @GetMapping
  public DashboardResponseDTO obtenerDashboard() {
    return dashboardService.obtenerDashboard();
  }

  @GetMapping("/filtrar")
  public DashboardResponseDTO obtenerDashboardPorFecha(

      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,

      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

    return dashboardService.obtenerDashboardPorFecha(
        fechaInicio,
        fechaFin);
  }

  @GetMapping("/exportar")
  public ResponseEntity<InputStreamResource> exportarExcel() {

    ByteArrayInputStream excel = dashboardService.exportarExcel();

    HttpHeaders headers = new HttpHeaders();

    headers.add(
        HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=dashboard.xlsx");

    return ResponseEntity.ok()
        .headers(headers)
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(new InputStreamResource(excel));
  }
}