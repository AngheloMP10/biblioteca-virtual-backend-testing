package com.biblio.virtual;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.biblio.virtual.model.Usuario;
import com.biblio.virtual.repository.IUsuarioRepository;
import com.biblio.virtual.security.Roles;

@SpringBootApplication
public class BibliotecaVirtualApplication {

	// Antes IUsuarioRepository y PasswordEncoder eran campos @Autowired de esta
	// clase. Como esta es
	// la clase de configuracion raiz, TODO test de slice (@WebMvcTest,
	// @DataJpaTest) intentaba
	// inyectarlos y fallaba con NoSuchBeanDefinitionException. Ahora son parametros
	// del @Bean: solo
	// se resuelven cuando el bean se crea, y con @Profile("!test") no se crea en
	// las pruebas.

	public static void main(String[] args) {
		SpringApplication.run(BibliotecaVirtualApplication.class, args);
	}

	@Bean
	@Profile("!test")
	public CommandLineRunner initData(IUsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
		return args -> {

			if (usuarioRepository.findByUsername("admin").isEmpty()) {
				Usuario admin = new Usuario();
				admin.setUsername("admin");
				admin.setPassword(passwordEncoder.encode("Admin123@"));
				admin.setRole(Roles.ADMIN);
				usuarioRepository.save(admin);
				System.out.println("Usuario ADMIN creado: admin / admin123");
			}

			if (usuarioRepository.findByUsername("bibliotecario").isEmpty()) {
				Usuario bibliotecario = new Usuario();
				bibliotecario.setUsername("bibliotecario");
				bibliotecario.setPassword(passwordEncoder.encode("Bibliotecario123@"));
				bibliotecario.setRole(Roles.BIBLIOTECARIO);
				usuarioRepository.save(bibliotecario);
				System.out.println("Usuario BIBLIOTECARIO creado: bibliotecario / bibliotecario123");
			}

			if (usuarioRepository.findByUsername("user").isEmpty()) {
				Usuario user = new Usuario();
				user.setUsername("user");
				user.setPassword(passwordEncoder.encode("User123@"));
				user.setRole(Roles.USER);
				usuarioRepository.save(user);
				System.out.println("Usuario USER creado: user / user123");
			}
		};
	}
}