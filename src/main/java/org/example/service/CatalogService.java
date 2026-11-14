package org.example.service;

import org.example.dto.CreateBookRequest;
import org.example.dto.CreateJournalRequest;
import org.example.exception.ItemNotFoundException;
import org.example.model.Book;
import org.example.model.Item;
import org.example.model.Journal;
import org.example.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * Бизнес-логика работы с каталогом. Ничего не знает ни о консоли, ни о файлах,
 * поэтому её же можно будет вызывать из REST-контроллера.
 */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ItemRepository repository;

    /**
     * Создаёт книгу и добавляет её в каталог.
     *
     * @param request данные книги
     * @return сохранённая книга с присвоенным ID
     */
    public Book addBook(CreateBookRequest request) {
        Book book = new Book(null, request.getName(), request.getPrice(), request.getAuthor(), request.getPageCount());
        return (Book) repository.save(book);
    }

    /**
     * Создаёт журнал и добавляет его в каталог.
     *
     * @param request данные журнала
     * @return сохранённый журнал с присвоенным ID
     */
    public Journal addJournal(CreateJournalRequest request) {
        Journal journal = new Journal(null, request.getName(), request.getPrice(), request.getPublisher(), request.getIssueNumber());
        return (Journal) repository.save(journal);
    }

    /** Возвращает все товары каталога. */
    public List<Item> getAll() {
        return repository.findAll();
    }

    /**
     * Возвращает товар по ID.
     *
     * @throws ItemNotFoundException если товара нет
     */
    public Item getById(long id) {
        return repository.findById(id).orElseThrow(() -> new ItemNotFoundException(id));
    }

    /**
     * Удаляет товар по ID.
     *
     * @throws ItemNotFoundException если товара нет
     */
    public void deleteById(long id) {
        if (!repository.deleteById(id)) {
            throw new ItemNotFoundException(id);
        }
    }

    /** Заменяет содержимое каталога переданными товарами. */
    public void replaceAll(Collection<? extends Item> items) {
        repository.replaceAll(items);
    }
}
