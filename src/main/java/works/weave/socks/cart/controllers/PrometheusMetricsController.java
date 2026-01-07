package works.weave.socks.cart.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

@RestController
public class PrometheusMetricsController {
    @Autowired
    private PrometheusMeterRegistry prometheusMeterRegistry;

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String metrics() {
        return prometheusMeterRegistry.scrape();
    }
}
