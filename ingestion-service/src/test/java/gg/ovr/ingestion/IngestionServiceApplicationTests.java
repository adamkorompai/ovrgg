package gg.ovr.ingestion;

import gg.ovr.ingestion.match.FakeMatchSource;
import gg.ovr.ingestion.match.MatchSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class IngestionServiceApplicationTests {

    @Autowired
    private MatchSource matchSource;

    @Test
    void usesFakeMatchSourceByDefault() {
        assertThat(matchSource).isInstanceOf(FakeMatchSource.class);
    }
}
