package dev.roadmap.coreioc.scopes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Prototype: the container creates a new instance on every lookup and does NOT manage its destruction. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ShoppingCart {

    private final String id = UUID.randomUUID().toString();
    private final List<String> items = new ArrayList<>();

    public String id() {
        return id;
    }

    public void add(String item) {
        items.add(item);
    }

    public List<String> items() {
        return List.copyOf(items);
    }
}
