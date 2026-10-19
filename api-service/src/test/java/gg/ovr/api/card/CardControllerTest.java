package gg.ovr.api.card;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;

import static gg.ovr.api.card.TestCards.card;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardController.class)
@Import(CardControllerTest.Beans.class)
class CardControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CardFeed feed;

    @Test
    void unknownCardIsNotFound() throws Exception {
        mvc.perform(get("/api/cards/puuid-unknown")).andExpect(status().isNotFound());
    }

    @Test
    void knownCardIsReturnedWithIsoTimestamp() throws Exception {
        feed.onCardUpdated(card("puuid-known", 73));

        mvc.perform(get("/api/cards/puuid-known"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ovr").value(73))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-19T12:00:00Z"));
    }

    @Test
    void streamSendsCurrentCardThenUpdates() throws Exception {
        feed.onCardUpdated(card("puuid-stream", 50));

        MvcResult result = mvc.perform(get("/api/cards/puuid-stream/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        feed.onCardUpdated(card("puuid-stream", 61, TestCards.UPDATED_AT.plusSeconds(1)));

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:card");
        assertThat(body.indexOf("\"ovr\":50")).isNotNegative().isLessThan(body.indexOf("\"ovr\":61"));
    }

    @Test
    void olderCardIsNotPushed() throws Exception {
        feed.onCardUpdated(card("puuid-old", 61, TestCards.UPDATED_AT.plusSeconds(1)));
        MvcResult result = mvc.perform(get("/api/cards/puuid-old/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        feed.onCardUpdated(card("puuid-old", 50));

        assertThat(result.getResponse().getContentAsString()).doesNotContain("\"ovr\":50");
    }

    @Test
    void streamOnlyReceivesItsOwnPlayer() throws Exception {
        MvcResult result = mvc.perform(get("/api/cards/puuid-a/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        feed.onCardUpdated(card("puuid-b", 61));

        assertThat(result.getResponse().getContentAsString()).doesNotContain("puuid-b");
    }

    @Test
    void streamIsTextEventStream() throws Exception {
        // Spring writes the SSE headers with the first event.
        feed.onCardUpdated(card("puuid-x", 50));

        mvc.perform(get("/api/cards/puuid-x/stream"))
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Beans {

        @Bean
        CardStore cardStore() {
            return new InMemoryCardStore();
        }

        @Bean
        CardStreamRegistry cardStreamRegistry() {
            return new CardStreamRegistry(Duration.ofMinutes(1));
        }

        @Bean
        CardFeed cardFeed(CardStore store, CardStreamRegistry streams) {
            return new CardFeed(store, streams);
        }
    }
}
