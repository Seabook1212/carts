package works.weave.socks.cart.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ContextConfiguration;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;
import works.weave.socks.cart.entities.HealthCheck;


import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest
@ContextConfiguration
public class UnitHealthCheckController {

    @Autowired
    private HealthCheckController healthCheckController;

    @Test
    public void shouldGetHealth() {
       Map<String, List<HealthCheck>> results = this.healthCheckController.getHealth();
       assertThat(results.get("health").size(), is(equalTo(2)));
    }

    @Configuration
    static class HealthCheckControllerTestConfiguration {
        @Bean
        public HealthCheckController healthCheckController() {
            return new HealthCheckController();
        }

        @Bean
        public MongoTemplate mongoTemplate() {
            MongoTemplate mongoTemplate = mock(MongoTemplate.class);
            return mongoTemplate;
        }
    }
}
