package gg.ovr.stats.performance;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;

@Configuration
@EnableKafkaStreams
class PerformanceConfig {

    /** Provisional until rank/role baselines exist (roadmap step 4). */
    @Bean
    PerformanceCalculator performanceCalculator() {
        return new ProvisionalPerformanceCalculator();
    }
}
