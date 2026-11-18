package dev.roadmap.reactive;

import org.springframework.boot.SpringApplication;

public class TestWebfluxApplication {

	public static void main(String[] args) {
		SpringApplication.from(WebfluxApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
