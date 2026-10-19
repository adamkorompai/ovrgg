package gg.ovr.card;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(PostgresTestConfig.class)
class CardServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
