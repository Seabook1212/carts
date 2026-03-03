package works.weave.socks.cart.cart;

import org.slf4j.Logger;
import works.weave.socks.cart.entities.Cart;
import works.weave.socks.cart.entities.Item;
import works.weave.socks.cart.util.MongoFailureClassifier;
import works.weave.socks.cart.util.TracingExceptionTagger;

import brave.Span;
import brave.Tracer;
import java.util.List;
import java.util.function.Supplier;

import static org.slf4j.LoggerFactory.getLogger;

public class CartContentsResource implements Contents<Item> {
    private final Logger LOG = getLogger(getClass());

    private final CartDAO cartRepository;
    private final Supplier<Resource<Cart>> parent;
    private final Tracer tracer;

    public CartContentsResource(CartDAO cartRepository, Supplier<Resource<Cart>> parent) {
        this(cartRepository, parent, null);
    }

    public CartContentsResource(CartDAO cartRepository, Supplier<Resource<Cart>> parent, Tracer tracer) {
        this.cartRepository = cartRepository;
        this.parent = parent;
        this.tracer = tracer;
    }

    private void inMongoSpan(String op, String customerId, String itemId, Runnable fn) {
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
            fn.run();
        } catch (Exception e) {
            if (span != null) {
                span.error(e);
                TracingExceptionTagger.tagException(span, e);
            }
            LOG.error(
                    "event=dependency_call_failed dependency=mongodb target=carts-db operation={} customerId={} itemId={} classification={}",
                    op,
                    customerId,
                    itemId,
                    MongoFailureClassifier.classify(e),
                    e);
            throw e;
        } finally {
            if (span != null) {
                span.finish();
            }
        }
    }

    @Override
    public Supplier<List<Item>> contents() {
        return () -> parentCart().contents();
    }

    @Override
    public Runnable add(Supplier<Item> item) {
        return () -> {
            Cart cart = parentCart();
            Item toAdd = item.get();
            LOG.debug("event=cart_item_add customerId={} itemId={} quantity={}", cart.customerId, toAdd.itemId(), toAdd.quantity());
            inMongoSpan("saveCart", cart.customerId, toAdd.itemId(), () -> cartRepository.save(cart.add(toAdd)));
        };
    }

    @Override
    public Runnable delete(Supplier<Item> item) {
        return () -> {
            Cart cart = parentCart();
            Item toDelete = item.get();
            LOG.debug("event=cart_item_delete customerId={} itemId={}", cart.customerId, toDelete.itemId());
            inMongoSpan("saveCart", cart.customerId, toDelete.itemId(), () -> cartRepository.save(cart.remove(toDelete)));
        };
    }

    private Cart parentCart() {
        return parent.get().value().get();
    }
}
