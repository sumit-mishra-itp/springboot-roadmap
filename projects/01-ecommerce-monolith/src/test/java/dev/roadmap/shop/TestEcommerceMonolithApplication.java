package dev.roadmap.shop;

import org.springframework.boot.SpringApplication;

public class TestEcommerceMonolithApplication {

	public static void main(String[] args) {
		SpringApplication.from(EcommerceMonolithApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
