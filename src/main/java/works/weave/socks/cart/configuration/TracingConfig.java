package works.weave.socks.cart.configuration;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

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
}
