package com.biblio.virtual.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TwoFactorAuthServiceTest {

    private TwoFactorAuthService service;
    private GoogleAuthenticator referencia;

    @BeforeEach
    void setUp() {
        service = new TwoFactorAuthService();
        referencia = new GoogleAuthenticator();
    }

    // ==================== generateSecret ====================
    // Verifica que se genere un secreto Base32 válido y no vacío.
    @Test
    void generateSecret_devuelveUnSecretoBase32NoVacio() {
        String secret = service.generateSecret();

        assertThat(secret).isNotNull();
        assertThat(secret).isNotBlank();
        assertThat(secret).matches("^[A-Z2-7]+$"); // alfabeto Base32 estándar
    }

    // Verifica que cada llamada genere un secreto diferente.
    @Test
    void generateSecret_generaValoresDistintosEnCadaLlamada() {
        String secret1 = service.generateSecret();
        String secret2 = service.generateSecret();

        assertThat(secret1).isNotEqualTo(secret2);
    }

    // ==================== validateCode ====================
    // Verifica que un código TOTP válido para el secreto actual sea aceptado.
    @Test
    void validateCode_codigoValidoParaElInstanteActual_retornaTrue() {
        String secret = service.generateSecret();
        int codigoValido = referencia.getTotpPassword(secret);

        assertThat(service.validateCode(secret, codigoValido)).isTrue();
    }

    // Verifica que un código que no corresponde al secreto sea rechazado.
    @Test
    void validateCode_codigoQueNoCorrespondeAlSecreto_retornaFalse() {
        String secret = service.generateSecret();
        int codigoValido = referencia.getTotpPassword(secret);
        int codigoInvalido = (codigoValido + 500_000) % 1_000_000;

        assertThat(service.validateCode(secret, codigoInvalido)).isFalse();
    }

    // Verifica que un código generado para otro secreto sea rechazado.
    @Test
    void validateCode_codigoDeSecretoDistinto_retornaFalse() {
        String secretA = service.generateSecret();
        String secretB = service.generateSecret();
        int codigoDeB = referencia.getTotpPassword(secretB);

        assertThat(service.validateCode(secretA, codigoDeB)).isFalse();
    }

    // Verifica que los códigos fuera del rango válido sean rechazados.
    @ParameterizedTest(name = "código={0} -> válido esperado={1}")
    @CsvSource({
            "0, false",
            "-1, false",
            "1, false",
            "999999999, false"
    })
    void validateCode_codigosFueraDeRango_retornanFalse(int codigo, boolean esperado) {
        String secret = service.generateSecret();

        assertThat(service.validateCode(secret, codigo)).isEqualTo(esperado);
    }

    // ==================== generateQrUrl ====================
    // Verifica que la URL generada tenga el formato correcto para autenticación
    // TOTP.
    @Test
    void generateQrUrl_formateaLaUrlOtpauthCorrectamente() {
        String url = service.generateQrUrl("jdoe", "ABCDEFGHIJKLMNOP");

        assertThat(url).isEqualTo(
                "otpauth://totp/BibliotecaVirtual:jdoe?secret=ABCDEFGHIJKLMNOP&issuer=BibliotecaVirtual");
    }

    // Verifica que la URL contenga el nombre de usuario y el secreto
    // proporcionados.
    @Test
    void generateQrUrl_incluyeElUsernameYElSecretoRecibidos() {
        String url = service.generateQrUrl("maria.lopez", "SECRETOTEST123");

        assertThat(url).contains("maria.lopez");
        assertThat(url).contains("SECRETOTEST123");
        assertThat(url).startsWith("otpauth://totp/BibliotecaVirtual:");
    }
}