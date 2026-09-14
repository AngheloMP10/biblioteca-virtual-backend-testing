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
    // Verifica que se pueda solicitar un préstamo correctamente cuando hay
    // disponibilidad.
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
    // Verifica que no se pueda solicitar un préstamo cuando el usuario ya tiene
    // tres préstamos activos.
    void solicitarPrestamo_limiteDeTresPrestamos_lanzaExcepcion() {
        when(prestamoRepo.countByUsuarioUsernameAndEstadoNotIn(eq("jdoe"), anyList())).thenReturn(3L);

        assertThrows(RuntimeException.class, () -> prestamoService.solicitarPrestamo(1L, "jdoe"));

        verify(prestamoRepo, never()).save(any());
        verify(notificacionService, never()).enviarNotificacion(any());
        verifyNoInteractions(usuarioRepo, libroRepo);
    }

    @Test
    // Verifica que se lance una excepción cuando el usuario no existe.
    void solicitarPrestamo_usuarioNoExiste_lanzaExcepcion() {
        when(prestamoRepo.countByUsuarioUsernameAndEstadoNotIn(eq("jdoe"), anyList())).thenReturn(0L);
        when(usuarioRepo.findByUsername("jdoe")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> prestamoService.solicitarPrestamo(1L, "jdoe"));

        verify(prestamoRepo, never()).save(any());
        verifyNoInteractions(libroRepo, notificacionService);
    }

    @Test
    // Verifica que se lance una excepción cuando el libro solicitado no existe.
    void solicitarPrestamo_libroNoExiste_lanzaExcepcion() {
        when(prestamoRepo.countByUsuarioUsernameAndEstadoNotIn(eq("jdoe"), anyList())).thenReturn(0L);
        when(usuarioRepo.findByUsername("jdoe")).thenReturn(Optional.of(usuario));
        when(libroRepo.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> prestamoService.solicitarPrestamo(1L, "jdoe"));

        verify(prestamoRepo, never()).save(any());
    }

    @Test
    // Verifica que no se pueda solicitar un préstamo cuando el libro no tiene stock
    // disponible.
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
    // Verifica que un préstamo pendiente pueda aprobarse y que se reduzca el stock
    // del libro.
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
    // Verifica que no se pueda aprobar un préstamo cuando el libro no tiene stock.
    void aprobarPrestamo_sinStock_lanzaExcepcion() {
        libro.setStock(0);
        when(prestamoRepo.findById(10L)).thenReturn(Optional.of(prestamo));

        assertThrows(RuntimeException.class, () -> prestamoService.aprobarPrestamo(10L));

        verify(prestamoRepo, never()).save(any());
        verify(libroRepo, never()).save(any());
        verify(notificacionService, never()).enviarNotificacion(any());
    }

    @Test
    // Verifica que se lance una excepción cuando el préstamo que se quiere aprobar
    // no existe.
    void aprobarPrestamo_noEncontrado_lanzaExcepcion() {
        when(prestamoRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> prestamoService.aprobarPrestamo(99L));
    }

    @Test
    // Verifica que un préstamo pendiente pueda rechazarse y se notifique al
    // usuario.
    void rechazarPrestamo_cambiaEstadoYNotifica() {
        prestamo.setEstado(EstadoPrestamo.PENDIENTE);
        when(prestamoRepo.findById(10L)).thenReturn(Optional.of(prestamo));

        prestamoService.rechazarPrestamo(10L);

        assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.RECHAZADO);
        verify(prestamoRepo).save(prestamo);
        verify(notificacionService).enviarNotificacion(any(NotificacionDTO.class));
    }

    @Test
    // Verifica que un préstamo aprobado pase a estar en préstamo y establezca la
    // fecha de devolución.
    void entregarPrestamo_estadoAprobado_cambiaAEnPrestamoYFijaFechaDevolucion() {
        prestamo.setEstado(EstadoPrestamo.APROBADO);
        when(prestamoRepo.findById(10L)).thenReturn(Optional.of(prestamo));

        prestamoService.entregarPrestamo(10L);

        assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.EN_PRESTAMO);
        assertThat(prestamo.getFechaDevolucion()).isEqualTo(LocalDate.now().plusDays(7));
        verify(prestamoRepo).save(prestamo);
    }

    @Test
    // Verifica que no se pueda entregar un préstamo que todavía no ha sido
    // aprobado.
    void entregarPrestamo_estadoNoAprobado_lanzaIllegalStateException() {
        prestamo.setEstado(EstadoPrestamo.PENDIENTE);
        when(prestamoRepo.findById(10L)).thenReturn(Optional.of(prestamo));

        assertThrows(IllegalStateException.class, () -> prestamoService.entregarPrestamo(10L));

        verify(prestamoRepo, never()).save(any());
    }

    @Test
    // Verifica que al finalizar un préstamo se recupere el stock y se actualice la
    // fecha de devolución.
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
    // Verifica que no se pueda finalizar un préstamo que no está actualmente en
    // préstamo.
    void finalizarPrestamo_estadoNoEnPrestamo_lanzaExcepcion() {
        prestamo.setEstado(EstadoPrestamo.PENDIENTE);
        when(prestamoRepo.findById(10L)).thenReturn(Optional.of(prestamo));

        assertThrows(RuntimeException.class, () -> prestamoService.finalizarPrestamo(10L));

        verify(prestamoRepo, never()).save(any());
        verify(libroRepo, never()).save(any());
        verify(notificacionService, never()).enviarNotificacion(any());
    }

    // ==================== Listados ====================

    @Test
    // Verifica que el método findAll obtenga correctamente todos los préstamos
    // desde el repositorio.
    void findAll_delegaAlRepositorio() {
        when(prestamoRepo.findAll()).thenReturn(List.of(prestamo));

        List<Prestamo> resultado = prestamoService.findAll();

        assertThat(resultado).containsExactly(prestamo);
    }

    @Test
    // Verifica que el método findByUsername obtenga los préstamos asociados a un
    // usuario.
    void findByUsername_delegaAlRepositorio() {
        when(prestamoRepo.findByUsuarioUsername("jdoe")).thenReturn(List.of(prestamo));

        List<Prestamo> resultado = prestamoService.findByUsername("jdoe");

        assertThat(resultado).containsExactly(prestamo);
    }
}