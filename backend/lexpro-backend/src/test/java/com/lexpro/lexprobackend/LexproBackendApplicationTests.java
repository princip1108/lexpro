package com.lexpro.lexprobackend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "LEXPRO_DB_PASSWORD=test-only")
class LexproBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
