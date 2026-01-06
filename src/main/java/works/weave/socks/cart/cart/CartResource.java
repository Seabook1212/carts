package works.weave.socks.cart.cart;

import works.weave.socks.cart.action.FirstResultOrDefault;
import works.weave.socks.cart.entities.Cart;

import java.util.function.Supplier;

import brave.Span;
import brave.Tracer;

public class CartResource implements Resource<Cart>, HasContents<CartContentsResource> {
    private final CartDAO cartRepository;
    private final String customerId;
    private final Tracer tracer;

    public CartResource(CartDAO cartRepository, String customerId, Tracer tracer) {
        this.cartRepository = cartRepository;
        this.customerId = customerId;
        this.tracer = tracer;
    }

    private <T> T inMongoSpan(String op, Supplier<T> fn) {
        if (tracer == null)
            return fn.get();

        Span span = tracer.nextSpan()
                .name("mongodb " + op)
                .kind(Span.Kind.CLIENT)
                .tag("db.system", "mongodb")
                .tag("peer.service", "carts-db")
                .start();

        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            return fn.get();
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.finish();
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
        // return () -> cartRepository.delete(value().get());
        return () -> inMongoSpan("deleteCart", () -> cartRepository.delete(value().get()));

    }

    @Override
    public Supplier<Cart> create() {
        // return () -> cartRepository.save(new Cart(customerId));
        return () -> inMongoSpan("createCart", () -> cartRepository.save(new Cart(customerId)));

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
        return () -> new CartContentsResource(cartRepository, () -> this);
    }
}
