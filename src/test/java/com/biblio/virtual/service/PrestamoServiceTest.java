package com.biblio.virtual.service;

import com.biblio.virtual.dto.NotificacionDTO;
import com.biblio.virtual.model.Libro;
import com.biblio.virtual.model.Prestamo;
import com.biblio.virtual.model.Usuario;
import com.biblio.virtual.model.enums.EstadoPrestamo;
import com.biblio.virtual.repository.ILibroRepository;
import com.biblio.virtual.repository.IPrestamoRepository;
import com.biblio.virtual.repository.IUsuarioRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrestamoServiceTest {

    @Mock
    private IPrestamoRepository prestamoRepo;
    @Mock
    private IUsuarioRepository usuarioRepo;
    @Mock
    private ILibroRepository libroRepo;
    @Mock
    private NotificacionService notificacionService;

    @InjectMocks
    private PrestamoService prestamoService;

    private Usuario usuario;
    private Libro libro;
    private Prestamo prestamo;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsername("jdoe");

        libro = new Libro();
        libro.setId(1L);
        libro.setStock(5);

        prestamo = new Prestamo();
        prestamo.setId(10L);
        prestamo.setUsuario(usuario);
        prestamo.setLibro(libro);
    }

    // ==================== Bloque 1: Solicitud de préstamos ====================

    @Test
    void solicitarPrestamo_caminoFeliz_creaPrestamoPendienteYNotifica() {
        when(prestamoRepo.countByUsuarioUsernameAndEstadoNotIn(eq("jdoe"), anyList())).thenReturn(1L);
        when(usuarioRepo.findByUsername("jdoe")).thenReturn(Optional.of(usuario));
        when(libroRepo.findById(1L)).thenReturn(Optional.of(libro));
        when(prestamoRepo.save(any(Prestamo.class))).thenAnswer(inv -> inv.getArgument(0));

        Prestamo resultado = prestamoService.solicitarPrestamo(1L, "jdoe");

        assertThat(resultado).isNotNull();
        assertThat(resultado.getEstado()).isEqualTo(EstadoPrestamo.PENDIENTE);
        assertThat(resultado.getUsuario()).isEqualTo(usuario);
        assertThat(resultado.getLibro()).isEqualTo(libro);

        verify(prestamoRepo).save(any(Prestamo.class));
        verify(notificacionService).enviarNotificacion(any(NotificacionDTO.class));
    }

    @Test
    void solicitarPrestamo_limiteDeTresPrestamos_lanzaExcepcion() {
        when(prestamoRepo.countByUsuarioUsernameAndEstadoNotIn(eq("jdoe"), anyList())).thenReturn(3L);

        assertThrows(RuntimeException.class, () -> prestamoService.solicitarPrestamo(1L, "jdoe"));

        verify(prestamoRepo, never()).save(any());
        verify(notificacionService, never()).enviarNotificacion(any());
        verifyNoInteractions(usuarioRepo, libroRepo);
    }

    @Test
    void solicitarPrestamo_usuarioNoExiste_lanzaExcepcion() {
        when(prestamoRepo.countByUsuarioUsernameAndEstadoNotIn(eq("jdoe"), anyList())).thenReturn(0L);
        when(usuarioRepo.findByUsername("jdoe")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> prestamoService.solicitarPrestamo(1L, "jdoe"));

        verify(prestamoRepo, never()).save(any());
        verifyNoInteractions(libroRepo, notificacionService);
    }

    @Test
    void solicitarPrestamo_libroNoExiste_lanzaExcepcion() {
        when(prestamoRepo.countByUsuarioUsernameAndEstadoNotIn(eq("jdoe"), anyList())).thenReturn(0L);
        when(usuarioRepo.findByUsername("jdoe")).thenReturn(Optional.of(usuario));
        when(libroRepo.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> prestamoService.solicitarPrestamo(1L, "jdoe"));

        verify(prestamoRepo, never()).save(any());
    }

    @Test
    void solicitarPrestamo_sinStock_lanzaExcepcion() {
        libro.setStock(0);
        when(prestamoRepo.countByUsuarioUsernameAndEstadoNotIn(eq("jdoe"), anyList())).thenReturn(0L);
        when(usuarioRepo.findByUsername("jdoe")).thenReturn(Optional.of(usuario));
        when(libroRepo.findById(1L)).thenReturn(Optional.of(libro));

        assertThrows(RuntimeException.class, () -> prestamoService.solicitarPrestamo(1L, "jdoe"));

        verify(prestamoRepo, never()).save(any());
        verify(notificacionService, never()).enviarNotificacion(any());
    }

    // ==================== Bloque 2: Transiciones de estado ====================

    @Test
    void aprobarPrestamo_conStockDisponible_cambiaEstadoYRestaStock() {
        prestamo.setEstado(EstadoPrestamo.PENDIENTE);
        when(prestamoRepo.findById(10L)).thenReturn(Optional.of(prestamo));

        prestamoService.aprobarPrestamo(10L);

        assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.APROBADO);
        assertThat(libro.getStock()).isEqualTo(4);

        verify(libroRepo).save(libro);
        verify(prestamoRepo).save(prestamo);
        verify(notificacionService).enviarNotificacion(any(NotificacionDTO.class));
    }

    @Test
    void aprobarPrestamo_sinStock_lanzaExcepcion() {
        prestamo.setEstado(EstadoPrestamo.PENDIENTE); // precondicion valida: aqui se prueba la regla de stock
        libro.setStock(0);
        when(prestamoRepo.findById(10L)).thenReturn(Optional.of(prestamo));

        assertThrows(RuntimeException.class, () -> prestamoService.aprobarPrestamo(10L));

        verify(prestamoRepo, never()).save(any());
        verify(libroRepo, never()).save(any());
        verify(notificacionService, never()).enviarNotificacion(any());
    }

    @Test
    void aprobarPrestamo_noEncontrado_lanzaExcepcion() {
        when(prestamoRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> prestamoService.aprobarPrestamo(99L));
    }

    @Test
    void rechazarPrestamo_cambiaEstadoYNotifica() {
        prestamo.setEstado(EstadoPrestamo.PENDIENTE);
        when(prestamoRepo.findById(10L)).thenReturn(Optional.of(prestamo));

        prestamoService.rechazarPrestamo(10L);

        assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.RECHAZADO);
        assertThat(libro.getStock()).isEqualTo(5); // PENDIENTE nunca desconto stock
        verify(libroRepo, never()).save(any());
        verify(prestamoRepo).save(prestamo);
        verify(notificacionService).enviarNotificacion(any(NotificacionDTO.class));
    }

    @Test
    void entregarPrestamo_estadoAprobado_cambiaAEnPrestamoYFijaFechaDevolucion() {
        prestamo.setEstado(EstadoPrestamo.APROBADO);
        when(prestamoRepo.findById(10L)).thenReturn(Optional.of(prestamo));

        prestamoService.entregarPrestamo(10L);

        assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.EN_PRESTAMO);
        assertThat(prestamo.getFechaDevolucion()).isEqualTo(LocalDate.now().plusDays(7));
        verify(prestamoRepo).save(prestamo);
    }

    @Test
    void entregarPrestamo_estadoNoAprobado_lanzaIllegalStateException() {
        prestamo.setEstado(EstadoPrestamo.PENDIENTE);
        when(prestamoRepo.findById(10L)).thenReturn(Optional.of(prestamo));

        assertThrows(IllegalStateException.class, () -> prestamoService.entregarPrestamo(10L));

        verify(prestamoRepo, never()).save(any());
    }

    @Test
    void finalizarPrestamo_estadoEnPrestamo_recuperaStockYFinaliza() {
        prestamo.setEstado(EstadoPrestamo.EN_PRESTAMO);
        libro.setStock(4);
        when(prestamoRepo.findById(10L)).thenReturn(Optional.of(prestamo));

        prestamoService.finalizarPrestamo(10L);

        assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.FINALIZADO);
        assertThat(prestamo.getFechaDevolucion()).isEqualTo(LocalDate.now());
        assertThat(libro.getStock()).isEqualTo(5);

        verify(libroRepo).save(libro);
        verify(prestamoRepo).save(prestamo);
        verify(notificacionService).enviarNotificacion(any(NotificacionDTO.class));
    }

    @Test
    void finalizarPrestamo_estadoNoEnPrestamo_lanzaExcepcion() {
        prestamo.setEstado(EstadoPrestamo.PENDIENTE);
        when(prestamoRepo.findById(10L)).thenReturn(Optional.of(prestamo));

        assertThrows(RuntimeException.class, () -> prestamoService.finalizarPrestamo(10L));

        verify(prestamoRepo, never()).save(any());
        verify(libroRepo, never()).save(any());
        verify(notificacionService, never()).enviarNotificacion(any());
    }

    // ==================== Regresion: BUG-01 y BUG-02 (precondiciones de estado e
    // inventario) ====================

    // BUG-01: aprobar un prestamo que ya no estaba PENDIENTE descontaba el stock
    // otra vez
    @ParameterizedTest(name = "aprobarPrestamo con estado {0} lanza IllegalStateException")
    @EnumSource(value = EstadoPrestamo.class, names = "PENDIENTE", mode = EnumSource.Mode.EXCLUDE)
    void aprobarPrestamo_estadoDistintoDePendiente_lanzaIllegalStateException(EstadoPrestamo estado) {
        prestamo.setEstado(estado);
        libro.setStock(4);
        when(prestamoRepo.findById(10L)).thenReturn(Optional.of(prestamo));

        assertThrows(IllegalStateException.class, () -> prestamoService.aprobarPrestamo(10L));

        assertThat(libro.getStock()).isEqualTo(4);
        assertThat(prestamo.getEstado()).isEqualTo(estado);
        verify(libroRepo, never()).save(any());
        verify(prestamoRepo, never()).save(any());
        verify(notificacionService, never()).enviarNotificacion(any());
    }

    @Test
    void aprobarPrestamo_dobleAprobacion_soloDescuentaUnaVez() {
        prestamo.setEstado(EstadoPrestamo.PENDIENTE);
        when(prestamoRepo.findById(10L)).thenReturn(Optional.of(prestamo));

        prestamoService.aprobarPrestamo(10L); // 1.a aprobacion: valida (5 -> 4)
        assertThrows(IllegalStateException.class, () -> prestamoService.aprobarPrestamo(10L)); // 2.a: rechazada

        assertThat(libro.getStock()).isEqualTo(4);
    }

    // BUG-02: rechazar un prestamo APROBADO no devolvia el stock que la aprobacion
    // ya habia descontado
    @Test
    void rechazarPrestamo_aprobado_devuelveElStockDescontado() {
        prestamo.setEstado(EstadoPrestamo.APROBADO);
        libro.setStock(4);
        when(prestamoRepo.findById(10L)).thenReturn(Optional.of(prestamo));

        prestamoService.rechazarPrestamo(10L);

        assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.RECHAZADO);
        assertThat(libro.getStock()).isEqualTo(5);
        verify(libroRepo).save(libro);
        verify(prestamoRepo).save(prestamo);
        verify(notificacionService).enviarNotificacion(any(NotificacionDTO.class));
    }

    @Test
    void inventario_aprobarYLuegoRechazar_dejaElStockIgualQueAlInicio() {
        prestamo.setEstado(EstadoPrestamo.PENDIENTE);
        when(prestamoRepo.findById(10L)).thenReturn(Optional.of(prestamo));
        int stockInicial = libro.getStock();

        prestamoService.aprobarPrestamo(10L);
        prestamoService.rechazarPrestamo(10L);

        assertThat(libro.getStock()).isEqualTo(stockInicial);
    }

    // Un prestamo ya entregado, finalizado o rechazado no puede volver a rechazarse
    @ParameterizedTest(name = "rechazarPrestamo con estado {0} lanza IllegalStateException")
    @EnumSource(value = EstadoPrestamo.class, names = { "EN_PRESTAMO", "FINALIZADO", "RECHAZADO" })
    void rechazarPrestamo_estadoNoRechazable_lanzaIllegalStateException(EstadoPrestamo estado) {
        prestamo.setEstado(estado);
        libro.setStock(4);
        when(prestamoRepo.findById(10L)).thenReturn(Optional.of(prestamo));

        assertThrows(IllegalStateException.class, () -> prestamoService.rechazarPrestamo(10L));

        assertThat(libro.getStock()).isEqualTo(4);
        assertThat(prestamo.getEstado()).isEqualTo(estado);
        verify(prestamoRepo, never()).save(any());
        verify(libroRepo, never()).save(any());
        verify(notificacionService, never()).enviarNotificacion(any());
    }

    // ==================== Listados ====================

    @Test
    void findAll_delegaAlRepositorio() {
        when(prestamoRepo.findAll()).thenReturn(List.of(prestamo));

        List<Prestamo> resultado = prestamoService.findAll();

        assertThat(resultado).containsExactly(prestamo);
    }

    @Test
    void findByUsername_delegaAlRepositorio() {
        when(prestamoRepo.findByUsuarioUsername("jdoe")).thenReturn(List.of(prestamo));

        List<Prestamo> resultado = prestamoService.findByUsername("jdoe");

        assertThat(resultado).containsExactly(prestamo);
    }
}