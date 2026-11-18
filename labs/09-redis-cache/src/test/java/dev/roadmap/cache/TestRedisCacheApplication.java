package dev.roadmap.cache;

import org.springframework.boot.SpringApplication;

public class TestRedisCacheApplication {

	public static void main(String[] args) {
		SpringApplication.from(RedisCacheApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
