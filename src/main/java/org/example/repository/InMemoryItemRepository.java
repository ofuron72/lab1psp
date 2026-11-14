package org.example.repository;

import org.example.model.Item;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class InMemoryItemRepository implements ItemRepository {

    private final Map<Long, Item> items = new LinkedHashMap<>();
    private long lastId = 0;

    @Override
    public synchronized List<Item> findAll() {
        return new ArrayList<>(items.values());
    }

    @Override
    public synchronized Optional<Item> findById(long id) {
        return Optional.ofNullable(items.get(id));
    }

    @Override
    public synchronized Item save(Item item) {
        if (item.getId() == null) {
            item.setId(++lastId);
        } else {
            lastId = Math.max(lastId, item.getId());
        }
        items.put(item.getId(), item);
        return item;
    }

    @Override
    public synchronized boolean deleteById(long id) {
        return items.remove(id) != null;
    }

    @Override
    public synchronized void replaceAll(Collection<? extends Item> newItems) {
        items.clear();
        lastId = 0;
        newItems.forEach(this::save);
    }
}
