package works.weave.socks.cart.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
// RunWith not needed in JUnit 5
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import works.weave.socks.cart.entities.Cart;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// @RunWith removed for JUnit 5
@EnableAutoConfiguration
public class ITCartRepository {
    @Autowired
    private CartRepository cartRepository;

    @BeforeEach
    public void removeAllData() {
        cartRepository.deleteAll();
    }

    @Test
    public void testCartSave() {
        Cart original = new Cart("customerId");
        Cart saved = cartRepository.save(original);

        assertEquals(1, cartRepository.count());
        assertEquals(original, saved);
    }

    @Test
    public void testCartGetDefault() {
        Cart original = new Cart("customerId");
        Cart saved = cartRepository.save(original);

        assertEquals(1, cartRepository.count());
        assertEquals(original, saved);
    }

    @Test
    public void testSearchCustomerById() {
        Cart original = new Cart("customerId");
        cartRepository.save(original);

        List<Cart> found = cartRepository.findByCustomerId(original.customerId);
        assertEquals(1, found.size());
        assertEquals(original, found.get(0));
    }
}
