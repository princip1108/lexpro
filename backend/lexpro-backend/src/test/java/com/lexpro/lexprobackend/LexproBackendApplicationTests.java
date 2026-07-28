package com.lexpro.lexprobackend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "LEXPRO_DB_PASSWORD=test-only",
        "LEXPRO_JWT_SECRET=test-only-secret-that-is-at-least-32-bytes-long"
})
class LexproBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
