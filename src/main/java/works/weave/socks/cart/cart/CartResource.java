package works.weave.socks.cart.cart;

import org.slf4j.Logger;
import works.weave.socks.cart.action.FirstResultOrDefault;
import works.weave.socks.cart.entities.Cart;
import works.weave.socks.cart.util.MongoFailureClassifier;
import works.weave.socks.cart.util.TracingExceptionTagger;

import java.util.function.Supplier;

import brave.Span;
import brave.Tracer;

import static org.slf4j.LoggerFactory.getLogger;

public class CartResource implements Resource<Cart>, HasContents<CartContentsResource> {
    private final Logger log = getLogger(getClass());
    private final CartDAO cartRepository;
    private final String customerId;
    private final Tracer tracer;

    public CartResource(CartDAO cartRepository, String customerId, Tracer tracer) {
        this.cartRepository = cartRepository;
        this.customerId = customerId;
        this.tracer = tracer;
    }

    private <T> T inMongoSpan(String op, Supplier<T> fn) {
        Span span = null;
        Tracer.SpanInScope spanInScope = null;
        if (tracer != null) {
            span = tracer.nextSpan()
                    .name("mongodb " + op)
                    .kind(Span.Kind.CLIENT)
                    .tag("db.system", "mongodb")
                    .tag("peer.service", "carts-db")
                    .start();
            spanInScope = tracer.withSpanInScope(span);
        }

        try (Tracer.SpanInScope ws = spanInScope) {
            return fn.get();
        } catch (Exception e) {
            if (span != null) {
                span.error(e);
                TracingExceptionTagger.tagException(span, e);
            }
            log.error(
                    "event=dependency_call_failed dependency=mongodb target=carts-db operation={} customerId={} classification={}",
                    op,
                    customerId,
                    MongoFailureClassifier.classify(e),
                    e);
            throw e;
        } finally {
            if (span != null) {
                span.finish();
            }
        }
    }

    private void inMongoSpan(String op, Runnable fn) {
        inMongoSpan(op, () -> {
            fn.run();
            return null;
        });
    }

    @Override
    public Runnable destroy() {
        return () -> inMongoSpan("deleteCart", () -> cartRepository.deleteByCustomerId(customerId));

    }

    @Override
    public Supplier<Cart> create() {
        // return () -> cartRepository.save(new Cart(customerId));
        return () -> inMongoSpan("createCart", () -> cartRepository.save(new Cart(customerId)));

    }

    public Runnable save(Cart cart) {
        return () -> inMongoSpan("saveCart", () -> cartRepository.save(cart));
    }

    @Override
    public Supplier<Cart> value() {
        return new FirstResultOrDefault<>(
                // cartRepository.findByCustomerId(customerId),
                inMongoSpan("findByCustomerId", () -> cartRepository.findByCustomerId(customerId)),
                () -> {
                    create().get();
                    return value().get();
                });
    }

    @Override
    public Runnable merge(Cart toMerge) {
        return () -> toMerge.contents().forEach(item -> contents().get().add(() -> item).run());
    }

    @Override
    public Supplier<CartContentsResource> contents() {
        return () -> new CartContentsResource(cartRepository, () -> this, tracer);
    }
}
