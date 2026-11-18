package dev.roadmap.jpa;

import org.springframework.boot.SpringApplication;

public class TestJpaDeepDiveApplication {

	public static void main(String[] args) {
		SpringApplication.from(JpaDeepDiveApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
