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

import java.util.List;

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
    public Item get(@PathVariable("customerId") String customerId, @PathVariable("itemId") String itemId) {
        return new FoundItem(() -> getItems(customerId), () -> new Item(itemId)).get();
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public List<Item> getItems(@PathVariable("customerId") String customerId) {
        return cartsController.get(customerId).contents();
    }

    @ResponseStatus(HttpStatus.CREATED)
    @RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public Item addToCart(@PathVariable("customerId") String customerId, @Valid @RequestBody Item item) {
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
    public void removeItem(@PathVariable("customerId") String customerId, @PathVariable("itemId") String itemId) {
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
    public void updateItem(@PathVariable("customerId") String customerId, @Valid @RequestBody Item item) {
        ItemResource itemResource = new ItemResource(itemDAO, () -> get(customerId, item.itemId()), tracer);
        LOG.debug(
                "event=item_merge customerId={} itemId={} quantity={}",
                customerId,
                item.itemId(),
                item.quantity());
        itemResource.merge(item).run();
    }
}
