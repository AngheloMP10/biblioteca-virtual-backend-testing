package com.biblio.virtual.repository;

import com.biblio.virtual.model.Genero;
import com.biblio.virtual.model.Libro;
import com.biblio.virtual.model.Prestamo;
import com.biblio.virtual.model.Usuario;
import com.biblio.virtual.model.enums.EstadoPrestamo;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Verifica las consultas reales del repositorio utilizando JPA, Hibernate y una base de datos H2 de prueba.
@Tag("nightly")
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(IPrestamoRepositoryTest.TestSecurityBeans.class)
class IPrestamoRepositoryTest {

    @TestConfiguration
    static class TestSecurityBeans {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private IPrestamoRepository prestamoRepository;

    // ==================== Helpers de construcción ====================

    private Usuario crearUsuario(String username) {
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword("password");
        usuario.setEmail(username + "@test.com");
        usuario.setRole("ROLE_USER");
        return entityManager.persistAndFlush(usuario);
    }

    private Genero crearGenero(String nombre) {
        Genero genero = new Genero();
        genero.setNombre(nombre);
        return entityManager.persistAndFlush(genero);
    }

    private Libro crearLibro(String titulo, int stock, Genero genero) {
        Libro libro = new Libro();
        libro.setTitulo(titulo);
        libro.setAnioPublicacion(2000);
        libro.setStock(stock);
        libro.setGenero(genero);
        return entityManager.persistAndFlush(libro);
    }

    private Prestamo crearPrestamo(Usuario usuario, Libro libro, EstadoPrestamo estado, LocalDate fechaSolicitud) {
        Prestamo prestamo = new Prestamo();
        prestamo.setUsuario(usuario);
        prestamo.setLibro(libro);
        prestamo.setEstado(estado);
        prestamo.setFechaSolicitud(fechaSolicitud);
        return entityManager.persistAndFlush(prestamo);
    }

    // ==================== countByUsuarioUsernameAndEstadoNotIn
    // ====================
    // Verifica que solo se cuenten los préstamos del usuario que no están
    // finalizados ni rechazados.
    @Test
    void countByUsuarioUsernameAndEstadoNotIn_cuentaSoloLosPrestamosActivos() {
        Genero genero = crearGenero("Ficción");
        Libro libro = crearLibro("Libro A", 5, genero);
        Usuario usuario = crearUsuario("jdoe");

        crearPrestamo(usuario, libro, EstadoPrestamo.PENDIENTE, LocalDate.now());
        crearPrestamo(usuario, libro, EstadoPrestamo.APROBADO, LocalDate.now());
        crearPrestamo(usuario, libro, EstadoPrestamo.FINALIZADO, LocalDate.now());
        crearPrestamo(usuario, libro, EstadoPrestamo.RECHAZADO, LocalDate.now());

        long activos = prestamoRepository.countByUsuarioUsernameAndEstadoNotIn(
                "jdoe", List.of(EstadoPrestamo.FINALIZADO, EstadoPrestamo.RECHAZADO));

        assertThat(activos).isEqualTo(2);
    }

    // Verifica que se retorne cero cuando el usuario no tiene préstamos.
    @Test
    void countByUsuarioUsernameAndEstadoNotIn_usuarioSinPrestamos_retornaCero() {
        crearUsuario("sinPrestamos");

        long activos = prestamoRepository.countByUsuarioUsernameAndEstadoNotIn(
                "sinPrestamos", List.of(EstadoPrestamo.FINALIZADO, EstadoPrestamo.RECHAZADO));

        assertThat(activos).isZero();
    }

    // ==================== findByUsuarioUsername ====================
    // Verifica que se obtengan únicamente los préstamos pertenecientes al usuario
    // indicado.
    @Test
    void findByUsuarioUsername_retornaSoloLosPrestamosDeEseUsuario() {
        Genero genero = crearGenero("Ficción");
        Libro libro = crearLibro("Libro A", 5, genero);
        Usuario usuario1 = crearUsuario("user1");
        Usuario usuario2 = crearUsuario("user2");

        crearPrestamo(usuario1, libro, EstadoPrestamo.PENDIENTE, LocalDate.now());
        crearPrestamo(usuario1, libro, EstadoPrestamo.APROBADO, LocalDate.now());
        crearPrestamo(usuario2, libro, EstadoPrestamo.PENDIENTE, LocalDate.now());

        List<Prestamo> resultado = prestamoRepository.findByUsuarioUsername("user1");

        assertThat(resultado).hasSize(2);
        assertThat(resultado).allSatisfy(p -> assertThat(p.getUsuario().getUsername()).isEqualTo("user1"));
    }

    // Verifica que se retorne una lista vacía cuando el usuario no tiene préstamos
    // registrados.
    @Test
    void findByUsuarioUsername_usuarioInexistente_retornaListaVacia() {
        List<Prestamo> resultado = prestamoRepository.findByUsuarioUsername("noExiste");

        assertThat(resultado).isEmpty();
    }

    // ==================== countByEstadoIn / countByEstado ====================
    // Verifica que se cuenten únicamente los préstamos que tienen alguno de los
    // estados indicados.
    @Test
    void countByEstadoIn_cuentaPrestamosEnLosEstadosIndicados() {
        Genero genero = crearGenero("Ficción");
        Libro libro = crearLibro("Libro A", 5, genero);
        Usuario usuario = crearUsuario("jdoe");

        crearPrestamo(usuario, libro, EstadoPrestamo.PENDIENTE, LocalDate.now());
        crearPrestamo(usuario, libro, EstadoPrestamo.APROBADO, LocalDate.now());
        crearPrestamo(usuario, libro, EstadoPrestamo.FINALIZADO, LocalDate.now());

        long total = prestamoRepository.countByEstadoIn(List.of(EstadoPrestamo.PENDIENTE, EstadoPrestamo.APROBADO));

        assertThat(total).isEqualTo(2);
    }

    // Verifica que se cuenten únicamente los préstamos que tienen exactamente el
    // estado indicado.
    @Test
    void countByEstado_cuentaSoloElEstadoIndicado() {
        Genero genero = crearGenero("Ficción");
        Libro libro = crearLibro("Libro A", 5, genero);
        Usuario usuario = crearUsuario("jdoe");

        crearPrestamo(usuario, libro, EstadoPrestamo.PENDIENTE, LocalDate.now());
        crearPrestamo(usuario, libro, EstadoPrestamo.PENDIENTE, LocalDate.now());
        crearPrestamo(usuario, libro, EstadoPrestamo.APROBADO, LocalDate.now());

        long pendientes = prestamoRepository.countByEstado(EstadoPrestamo.PENDIENTE);

        assertThat(pendientes).isEqualTo(2);
    }
}