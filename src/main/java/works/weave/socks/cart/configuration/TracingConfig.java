package works.weave.socks.cart.configuration;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class TracingConfig {

    /**
     * Configure ObservationRegistry to skip tracing for actuator endpoints
     * Uses BeanPostProcessor to avoid circular dependency issues
     */
    @Bean
    public BeanPostProcessor observationRegistryConfigurer() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof ObservationRegistry registry) {
                    // Add predicate to skip actuator endpoints
                    registry.observationConfig().observationPredicate((name, context) -> {
                        // Check if this is an HTTP server observation
                        if (context instanceof ServerRequestObservationContext serverContext) {
                            String uri = serverContext.getCarrier().getRequestURI();

                            // Skip observation (and thus tracing) for actuator endpoints
                            if (uri != null && (uri.startsWith("/actuator") ||
                                               uri.equals("/health") ||
                                               uri.equals("/metrics"))) {
                                return false; // Don't observe these endpoints
                            }
                        }
                        return true; // Observe all other endpoints
                    });
                }
                return bean;
            }
        };
    }

    @Bean
    public SpanHandler kubernetesTraceTagsSpanHandler(
            @Value("${CONTAINER_NAME:}") String containerName,
            @Value("${POD_NAME:}") String podName,
            @Value("${POD_NAMESPACE:}") String podNamespace,
            @Value("${NODE_NAME:}") String nodeName) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("container", containerName);
        tags.put("pod", podName);
        tags.put("namespace", podNamespace);
        tags.put("node", nodeName);

        return new SpanHandler() {
            @Override
            public boolean end(TraceContext context, MutableSpan span, Cause cause) {
                tags.forEach((key, value) -> {
                    if (StringUtils.hasText(value)) {
                        span.tag(key, value);
                    }
                });
                return true;
            }
        };
    }
}
