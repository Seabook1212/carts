package works.weave.socks.cart.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
// RunWith not needed in JUnit 5
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import works.weave.socks.cart.entities.Item;

import static org.junit.jupiter.api.Assertions.assertEquals;

// @RunWith removed for JUnit 5
@EnableAutoConfiguration
public class ITItemRepository {
    @Autowired
    private ItemRepository itemRepository;

    @BeforeEach
    public void removeAllData() {
        itemRepository.deleteAll();
    }

    @Test
    public void testCustomerSave() {
        Item original = new Item("id", "itemId", 1, 0.99F);
        Item saved = itemRepository.save(original);

        assertEquals(1, itemRepository.count());
        assertEquals(original, saved);
    }
}
