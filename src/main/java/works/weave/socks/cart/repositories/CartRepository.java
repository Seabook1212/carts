package works.weave.socks.cart.repositories;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import works.weave.socks.cart.entities.Cart;

@RepositoryRestResource(exported = false)
public interface CartRepository extends MongoRepository<Cart, String> {
    List<Cart> findByCustomerId(@Param("custId") String id);

    void deleteByCustomerId(@Param("custId") String id);
}
