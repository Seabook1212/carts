package works.weave.socks.cart.configuration;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.*;
import io.micrometer.core.instrument.binder.system.*;
import io.micrometer.core.instrument.binder.logging.*;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Configuration
public class MetricsConfig {

    @Autowired
    private PrometheusMeterRegistry registry;

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags("application", "carts");
    }

    @Bean
    public CommandLineRunner bindSystemMetrics(MeterRegistry registry) {
        return args -> {
            // 所有指标都会带上 commonTags 中的 tag（包括自动注册的和自定义注册的）
            new ClassLoaderMetrics().bindTo(registry);
            new JvmMemoryMetrics().bindTo(registry);
            new JvmGcMetrics().bindTo(registry);
            new ProcessorMetrics().bindTo(registry);
            new JvmThreadMetrics().bindTo(registry);
            new UptimeMetrics().bindTo(registry);
            new FileDescriptorMetrics().bindTo(registry);
        };
    }

    // @RequestMapping("/metrics")
    // public void redirectToPrometheus(HttpServletRequest request,
    // HttpServletResponse response) throws Exception {
    // // 直接转发到 Spring Boot actuator 的 /actuator/prometheus 接口
    // request.getRequestDispatcher("/actuator/prometheus").forward(request,
    // response);
    // }
}
