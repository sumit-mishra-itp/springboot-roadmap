package dev.roadmap.jpa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// @EnableJpaAuditing sits on the main class so @DataJpaTest slices (which load this class) get auditing too.
@SpringBootApplication
@EnableJpaAuditing
public class JpaDeepDiveApplication {

	public static void main(String[] args) {
		SpringApplication.run(JpaDeepDiveApplication.class, args);
	}

}
