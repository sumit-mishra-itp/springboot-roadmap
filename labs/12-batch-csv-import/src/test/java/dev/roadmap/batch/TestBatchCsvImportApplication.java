package dev.roadmap.batch;

import org.springframework.boot.SpringApplication;

public class TestBatchCsvImportApplication {

	public static void main(String[] args) {
		SpringApplication.from(BatchCsvImportApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
