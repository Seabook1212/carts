package works.weave.socks.cart.item;

import brave.Span;
import brave.Tracer;
import org.slf4j.Logger;
import works.weave.socks.cart.cart.Resource;
import works.weave.socks.cart.entities.Item;
import works.weave.socks.cart.util.MongoFailureClassifier;
import works.weave.socks.cart.util.TracingExceptionTagger;

import java.util.function.Supplier;

import static org.slf4j.LoggerFactory.getLogger;

public class ItemResource implements Resource<Item> {
    private final Logger log = getLogger(getClass());
    private final ItemDAO itemRepository;
    private final Supplier<Item> item;
    private final Tracer tracer;

    public ItemResource(ItemDAO itemRepository, Supplier<Item> item) {
        this(itemRepository, item, null);
    }

    public ItemResource(ItemDAO itemRepository, Supplier<Item> item, Tracer tracer) {
        this.itemRepository = itemRepository;
        this.item = item;
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
                    "event=dependency_call_failed dependency=mongodb target=carts-db operation={} itemId={} classification={}",
                    op,
                    safeItemId(),
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
    public Runnable destroy() {
        return () -> inMongoSpan("deleteItem", () -> {
            itemRepository.destroy(value().get());
            return null;
        });
    }

    @Override
    public Supplier<Item> create() {
        return () -> inMongoSpan("createItem", () -> itemRepository.save(item.get()));
    }

    @Override
    public Supplier<Item> value() {
        return item;    // Basically a null method. Gets the item from the supplier.
    }

    @Override
    public Runnable merge(Item toMerge) {
        return () -> inMongoSpan("updateItem", () -> {
            itemRepository.save(new Item(value().get(), toMerge.quantity()));
            return null;
        });
    }

    private String safeItemId() {
        try {
            Item current = item.get();
            return current == null ? null : current.itemId();
        } catch (Exception ignored) {
            return null;
        }
    }
}
