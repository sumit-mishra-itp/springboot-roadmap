package dev.roadmap.coreioc.scopes;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Singleton that depends on a prototype.
 * - injectedOnce: the classic trap. Injected ONCE at singleton creation, so every caller shares one cart.
 * - provider: ObjectProvider.getObject() asks the container each time, so each call gets a fresh cart.
 */
@Service
public class CartService {

    private final ShoppingCart injectedOnce;
    private final ObjectProvider<ShoppingCart> provider;

    public CartService(ShoppingCart injectedOnce, ObjectProvider<ShoppingCart> provider) {
        this.injectedOnce = injectedOnce;
        this.provider = provider;
    }

    public ShoppingCart staleCart() {
        return injectedOnce;
    }

    public ShoppingCart newCart() {
        return provider.getObject();
    }
}
