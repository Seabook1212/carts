package works.weave.socks.cart.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import works.weave.socks.cart.cart.CartDAO;
import works.weave.socks.cart.entities.Cart;
import works.weave.socks.cart.entities.Item;
import works.weave.socks.cart.item.ItemDAO;
import works.weave.socks.cart.repositories.CartRepository;
import works.weave.socks.cart.repositories.ItemRepository;

import java.util.List;

import javax.annotation.PostConstruct;

@Configuration
public class BeanConfiguration {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    PrometheusMeterRegistry prometheusMeterRegistry;

    @PostConstruct
    public void init() {
        logger.info("✅ Prometheus registry initialized: " + prometheusMeterRegistry.getClass());
    }

    @Bean
    @Autowired
    public CartDAO getCartDao(CartRepository cartRepository) {
        return new CartDAO() {
            @Override
            public void delete(Cart cart) {
                cartRepository.delete(cart);
            }

            @Override
            public Cart save(Cart cart) {
                return cartRepository.save(cart);
            }

            @Override
            public List<Cart> findByCustomerId(String customerId) {
                return cartRepository.findByCustomerId(customerId);
            }
        };
    }

    @Bean
    @Autowired
    public ItemDAO getItemDao(ItemRepository itemRepository) {
        return new ItemDAO() {
            @Override
            public Item save(Item item) {
                return itemRepository.save(item);
            }

            @Override
            public void destroy(Item item) {
                itemRepository.delete(item);
            }

            @Override
            public Item findOne(String id) {
                return itemRepository.findById(id).orElse(null);
            }
        };
    }

    // @Bean
    // public PrometheusMeterRegistry prometheusMeterRegistry() {
    // return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    // }

    // @Bean
    // public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
    // return registry -> registry.config().commonTags("application", "carts");
    // }

    // @Bean
    // public MeterRegistryCustomizer<MeterRegistry> bindMetrics() {
    // return registry -> {
    // new ClassLoaderMetrics().bindTo(registry);
    // new JvmMemoryMetrics().bindTo(registry);
    // new JvmGcMetrics().bindTo(registry);
    // new ProcessorMetrics().bindTo(registry);
    // new JvmThreadMetrics().bindTo(registry);
    // new UptimeMetrics().bindTo(registry);
    // new FileDescriptorMetrics().bindTo(registry);
    // new LogbackMetrics().bindTo(registry);
    // };
    // }
}
