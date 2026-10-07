package com.example.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.flywaydb.core.Flyway;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:backend-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
		"spring.datasource.username=sa",
		"spring.datasource.password="
})
class BackendApplicationTests {

	@Autowired
	private Flyway flyway;

	@Test
	void contextLoads() {
	}

	@Test
	void appliesCoreSchemaMigration() {
		org.junit.jupiter.api.Assertions.assertEquals(1, flyway.info().applied().length);
	}
}
