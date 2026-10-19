package gg.ovr.api.player;

import gg.ovr.common.player.PlayerLookupRequested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LookupController.class)
@Import(LookupControllerTest.Fakes.class)
class LookupControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private List<PlayerLookupRequested> published;

    @BeforeEach
    void setUp() {
        published.clear();
    }

    @Test
    void acceptsLookupAndReturnsPuuid() throws Exception {
        mvc.perform(post("/api/lookups").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameName": "Alice", "tagLine": "EUW"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.puuid").value("puuid-alice"));

        assertThat(published).extracting(PlayerLookupRequested::puuid).containsExactly("puuid-alice");
    }

    @Test
    void invalidRiotIdIsBadRequest() throws Exception {
        mvc.perform(post("/api/lookups").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameName": "Alice", "tagLine": "E"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("tagLine")));

        assertThat(published).isEmpty();
    }

    @Test
    void unknownAccountIsNotFound() throws Exception {
        mvc.perform(post("/api/lookups").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameName": "Nobody", "tagLine": "EUW"}"""))
                .andExpect(status().isNotFound());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fakes {

        @Bean
        List<PlayerLookupRequested> published() {
            return new ArrayList<>();
        }

        @Bean
        LookupService lookupService(List<PlayerLookupRequested> published) {
            AccountResolver resolver = riotId -> riotId.gameName().equals("Nobody")
                    ? Optional.empty()
                    : Optional.of("puuid-" + riotId.gameName().toLowerCase());
            return new LookupService(resolver, published::add, Clock.systemUTC());
        }
    }
}
