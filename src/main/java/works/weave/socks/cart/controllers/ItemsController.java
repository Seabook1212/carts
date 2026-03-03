package works.weave.socks.cart.controllers;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import brave.Tracer;
import works.weave.socks.cart.cart.CartDAO;
import works.weave.socks.cart.cart.CartResource;
import works.weave.socks.cart.entities.Cart;
import works.weave.socks.cart.entities.Item;
import works.weave.socks.cart.item.FoundItem;
import works.weave.socks.cart.item.ItemDAO;
import works.weave.socks.cart.item.ItemResource;
import works.weave.socks.cart.util.CartsCommonHelper;
import works.weave.socks.cart.util.TailLatencyFault;

import java.util.List;
import java.util.function.Supplier;

import static org.slf4j.LoggerFactory.getLogger;

@RestController
@RequestMapping(value = "/carts/{customerId:.*}/items")
public class ItemsController {
    private final Logger LOG = getLogger(getClass());

    @Autowired
    private ItemDAO itemDAO;
    @Autowired
    private CartsController cartsController;
    @Autowired
    private CartDAO cartDAO;

    @Autowired
    private Tracer tracer;

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{itemId:.*}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public Item get(@PathVariable String customerId, @PathVariable String itemId) {
        return new FoundItem(() -> getItems(customerId), () -> new Item(itemId)).get();
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public List<Item> getItems(@PathVariable String customerId) {
        return cartsController.get(customerId).contents();
    }

    @ResponseStatus(HttpStatus.CREATED)
    @RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public Item addToCart(@PathVariable String customerId, @Valid @RequestBody Item item) {
        TailLatencyFault.maybeInject(10);
        doExceptionInject(customerId);

        // If the item does not exist in the cart, create new one in the repository.
        FoundItem foundItem = new FoundItem(() -> cartsController.get(customerId).contents(), () -> item);
        if (!foundItem.hasItem()) {
            Item createdItem = new ItemResource(itemDAO, () -> item, tracer).create().get();
            LOG.debug(
                    "event=item_create customerId={} itemId={} quantity={}",
                    customerId,
                    createdItem.itemId(),
                    createdItem.quantity());
            new CartResource(cartDAO, customerId, tracer).contents().get().add(() -> createdItem).run();
            return createdItem;
        } else {
            Item newItem = new Item(foundItem.get(), foundItem.get().quantity() + 1);
            LOG.debug(
                    "event=item_increment customerId={} itemId={} quantity={}",
                    customerId,
                    newItem.itemId(),
                    newItem.quantity());
            updateItem(customerId, newItem);
            return newItem;
        }
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequestMapping(value = "/{itemId:.*}", method = RequestMethod.DELETE)
    public void removeItem(@PathVariable String customerId, @PathVariable String itemId) {
        CartResource cartResource = new CartResource(cartDAO, customerId, tracer);
        Cart cart = cartResource.value().get();
        Item item = new FoundItem(cart::contents, () -> new Item(itemId)).get();

        LOG.debug("event=item_remove_from_cart customerId={} itemId={}", customerId, item.itemId());
        cartResource.save(cart.remove(item)).run();

        LOG.debug("event=item_remove_from_repository customerId={} itemId={}", customerId, item.itemId());
        new ItemResource(itemDAO, () -> item, tracer).destroy().run();
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.PATCH)
    public void updateItem(@PathVariable String customerId, @Valid @RequestBody Item item) {
        doExceptionInjectV2();
        doExceptionInjectV3(item);

        ItemResource itemResource = new ItemResource(itemDAO, () -> get(customerId, item.itemId()), tracer);
        LOG.debug(
                "event=item_merge customerId={} itemId={} quantity={}",
                customerId,
                item.itemId(),
                item.quantity());

        // EX-02: NPE baseline - simulate common null pointer issue
        if (CartsCommonHelper.envTrue("FAULTS_ENABLED")
                && CartsCommonHelper.envTrue("FAULT_EX02_ENABLED")) {
            if ("EX-02".equalsIgnoreCase(CartsCommonHelper.header("X-Fault"))
                    || CartsCommonHelper.envTrue("FAULT_EX02_ALWAYS")) {
                itemResource = null;
            }
        }
        itemResource.merge(item).run();
    }

    private void doExceptionInject(String customerId) {
        if (CartsCommonHelper.envTrue("FAULTS_ENABLED") && CartsCommonHelper.envTrue("FAULT_EX01_ENABLED")) {
            if ("EX-01".equalsIgnoreCase(CartsCommonHelper.header("X-Fault"))
                    || CartsCommonHelper.envTrue("FAULT_EX01_ALWAYS")) {
                CartResource cart = new CartResource(cartDAO, customerId, tracer);

                // 人为制造非法业务状态
                if (cart.value().get().contents() == null) {
                    // 正常情况下 contents() 不应为 null
                    throw new IllegalStateException("Cart contents must not be null");
                }
            }
        }
    }

    private void doExceptionInjectV2() {
        // IAE-01: IllegalArgumentException injection (baseline)
        if (CartsCommonHelper.envTrue("FAULTS_ENABLED")
                && CartsCommonHelper.envTrue("FAULT_IAE01_ENABLED")) {
            if ("IAE-01".equalsIgnoreCase(CartsCommonHelper.header("X-Fault"))
                    || CartsCommonHelper.envTrue("FAULT_IAE01_ALWAYS")) {

                // 这里写得更像真实 bug：参数/业务前置条件不满足
                throw new IllegalArgumentException(
                        "Invalid cart item update: itemId must not be null/empty and quantity must be >= 1");
            }
        }
    }

    private void doExceptionInjectV3(Item item) {
        // ARITH-01: ArithmeticException (/ by zero)
        if (CartsCommonHelper.envTrue("FAULTS_ENABLED")
                && CartsCommonHelper.envTrue("FAULT_ARITH01_ENABLED")) {
            if ("ARITH-01".equalsIgnoreCase(CartsCommonHelper.header("X-Fault"))
                    || CartsCommonHelper.envTrue("FAULT_ARITH01_ALWAYS")) {

                // 模拟真实业务计算错误：用数量作为分母
                int quantity = (item == null ? 0 : item.quantity() - 2);
                // 当 quantity == 2 时，这里会自然抛出：
                // java.lang.ArithmeticException: / by zero
                int avg = 100 / quantity;

                // 防止编译器警告（实际上永远走不到）
                LOG.debug("avg={}", avg);

            }
        }
    }
}
