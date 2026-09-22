package com.example.productionapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductionApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
