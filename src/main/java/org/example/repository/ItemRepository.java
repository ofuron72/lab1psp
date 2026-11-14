package org.example.repository;

import org.example.model.Item;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Абстракция хранилища товаров. Сейчас реализована в памяти;
 */
public interface ItemRepository {

    /** Возвращает все товары. */
    List<Item> findAll();

    /** Ищет товар по ID. */
    Optional<Item> findById(long id);

    /** Сохраняет товар; если id не задан — присваивает новый. */
    Item save(Item item);

    /**
     * Удаляет товар по ID.
     *
     * @return {@code true}, если товар был удалён
     */
    boolean deleteById(long id);

    /** Полностью заменяет содержимое хранилища (используется при загрузке из файла). */
    void replaceAll(Collection<? extends Item> items);
}
