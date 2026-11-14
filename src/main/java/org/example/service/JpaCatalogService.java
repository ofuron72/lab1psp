package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.CreateBookRequest;
import org.example.dto.CreateJournalRequest;
import org.example.exception.ItemNotFoundException;
import org.example.model.Book;
import org.example.model.Item;
import org.example.model.Journal;
import org.example.repository.jpa.JpaItemRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Операции ЛР №4: данные хранятся в БД, JSON-файл не используется. */
@Service
@Profile("db")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaCatalogService {
    private final JpaItemRepository repository;

    public List<Item> getAll() {
        return repository.findAll(Sort.by("id"));
    }

    public Item getById(long id) {
        return repository.findById(id).orElseThrow(() -> new ItemNotFoundException(id));
    }

    @Transactional
    public Book addBook(CreateBookRequest request) {
        var book = new Book();
        fillBook(book, request);
        return repository.save(book);
    }

    @Transactional
    public Journal addJournal(CreateJournalRequest request) {
        var journal = new Journal();
        fillJournal(journal, request);
        return repository.save(journal);
    }

    @Transactional
    public Book updateBook(long id, CreateBookRequest request) {
        if (!(getById(id) instanceof Book book)) throw new IllegalArgumentException("Товар не является книгой.");
        fillBook(book, request);
        return repository.save(book);
    }

    @Transactional
    public Journal updateJournal(long id, CreateJournalRequest request) {
        if (!(getById(id) instanceof Journal journal)) throw new IllegalArgumentException("Товар не является журналом.");
        fillJournal(journal, request);
        return repository.save(journal);
    }

    @Transactional
    public void deleteById(long id) {
        repository.delete(getById(id));
    }

    private void fillBook(Book book, CreateBookRequest request) {
        if (request == null) throw new IllegalArgumentException("Не указаны данные книги.");
        String name = text(request.getName());
        String author = text(request.getAuthor());
        BigDecimal price = price(request.getPrice());
        positive(request.getPageCount());
        book.setName(name);
        book.setPrice(price);
        book.setAuthor(author);
        book.setPageCount(request.getPageCount());
    }

    private void fillJournal(Journal journal, CreateJournalRequest request) {
        if (request == null) throw new IllegalArgumentException("Не указаны данные журнала.");
        String name = text(request.getName());
        String publisher = text(request.getPublisher());
        BigDecimal price = price(request.getPrice());
        positive(request.getIssueNumber());
        journal.setName(name);
        journal.setPrice(price);
        journal.setPublisher(publisher);
        journal.setIssueNumber(request.getIssueNumber());
    }

    private String text(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 255 || value.matches("[\\d\\s.,+-]+")) {
            throw new IllegalArgumentException("Ожидается текст от 1 до 255 символов.");
        }
        return value.trim();
    }

    private BigDecimal price(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Цена должна быть неотрицательной.");
        try {
            value = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("У цены может быть не более двух знаков после запятой.");
        }
        if (value.precision() > 19) throw new IllegalArgumentException("Цена слишком большая.");
        return value;
    }

    private void positive(int value) {
        if (value <= 0) throw new IllegalArgumentException("Число должно быть больше нуля.");
    }
}
