package com.biblio.virtual.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.biblio.virtual.service.IDashboardService;

/**
 * Regresion del hallazgo de APF2 (Broken Access Control): antes del parche
 * cualquier usuario
 * autenticado con ROLE_USER podia leer y exportar las metricas de toda la
 * biblioteca.
 *
 * Usa el contexto completo con la SecurityConfig real (perfil "test", H2 en
 * memoria) y un
 * IDashboardService simulado, de modo que se prueba la regla de autorizacion y
 * no la logica
 * de negocio del dashboard.
 *
 * Evidencia "rojo -> verde": ejecutar este test con el DashboardController SIN
 * el parche
 * (los casos de ROLE_USER y ROLE_BIBLIOTECARIO fallan con 200 en lugar de 403)
 * y luego con
 * el parche aplicado (todos pasan).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("regression")
@DisplayName("Regresion APF2: control de acceso del Dashboard (solo ADMIN)")
class DashboardControllerTest {

    private static final String FILTRAR = "/dashboard/filtrar?fechaInicio=2026-01-01&fechaFin=2026-01-31";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IDashboardService dashboardService;

    @BeforeEach
    void stubExportar() {
        // new InputStreamResource(null) lanzaria IllegalArgumentException si el mock
        // devolviera null
        when(dashboardService.exportarExcel()).thenReturn(new ByteArrayInputStream(new byte[0]));
    }

    @ParameterizedTest(name = "ROLE_USER recibe 403 en {0}")
    @ValueSource(strings = { "/dashboard", FILTRAR, "/dashboard/exportar" })
    @WithMockUser(username = "user", authorities = "ROLE_USER")
    void usuarioNormal_noPuedeAccederAlDashboard(String url) throws Exception {
        mockMvc.perform(get(url)).andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "ROLE_BIBLIOTECARIO recibe 403 en {0}")
    @ValueSource(strings = { "/dashboard", FILTRAR, "/dashboard/exportar" })
    @WithMockUser(username = "biblio", authorities = "ROLE_BIBLIOTECARIO")
    void bibliotecario_noPuedeAccederAlDashboard(String url) throws Exception {
        mockMvc.perform(get(url)).andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "ROLE_ADMIN recibe 200 en {0}")
    @ValueSource(strings = { "/dashboard", FILTRAR, "/dashboard/exportar" })
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void admin_puedeAccederAlDashboard(String url) throws Exception {
        mockMvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test
    void sinAutenticar_noPuedeAccederAlDashboard() throws Exception {
        mockMvc.perform(get("/dashboard")).andExpect(status().is4xxClientError());
    }
}