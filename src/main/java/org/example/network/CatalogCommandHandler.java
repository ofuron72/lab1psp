package org.example.network;

import lombok.RequiredArgsConstructor;
import org.example.exception.CatalogFileWriteException;
import org.example.exception.ItemNotFoundException;
import org.example.service.CatalogFileService;
import org.example.service.CatalogService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class CatalogCommandHandler {
    private final CatalogService catalog;
    private final CatalogFileService files;

    // Блокируем только операцию с каталогом, а не ожидание данных от клиента.
    // Снимок каталога и запись в файл выполняются под той же блокировкой.
    public synchronized CatalogResponse execute(CatalogRequest request) {
        try {
            if (request == null || request.command() == null) {
                throw new IllegalArgumentException("Не указана команда.");
            }
            return switch (request.command()) {
                case "LIST" -> new CatalogResponse(true, "Каталог", catalog.getAll());
                case "ADD_BOOK" -> {
                    var book = request.book();
                    if (book == null) throw new IllegalArgumentException("Не указаны данные книги.");
                    validateText(book.getName());
                    validateText(book.getAuthor());
                    validatePrice(book.getPrice());
                    validatePositive(book.getPageCount());
                    var item = catalog.addBook(book);
                    yield CatalogResponse.ok("Книга добавлена, ID = " + item.getId());
                }
                case "ADD_JOURNAL" -> {
                    var journal = request.journal();
                    if (journal == null) throw new IllegalArgumentException("Не указаны данные журнала.");
                    validateText(journal.getName());
                    validateText(journal.getPublisher());
                    validatePrice(journal.getPrice());
                    validatePositive(journal.getIssueNumber());
                    var item = catalog.addJournal(journal);
                    yield CatalogResponse.ok("Журнал добавлен, ID = " + item.getId());
                }
                case "DELETE" -> {
                    if (request.id() == null) throw new IllegalArgumentException("Не указан ID.");
                    catalog.deleteById(request.id());
                    yield CatalogResponse.ok("Товар удалён.");
                }
                case "SAVE", "QUIT" -> {
                    save();
                    yield CatalogResponse.ok("Каталог сохранён.");
                }
                default -> CatalogResponse.error("Неизвестная команда: " + request.command());
            };
        } catch (IllegalArgumentException | ItemNotFoundException | CatalogFileWriteException e) {
            return CatalogResponse.error(e.getMessage());
        }
    }

    public synchronized void save() {
        files.save(catalog.getAll());
    }

    private void validateText(String value) {
        if (value == null || value.isBlank() || value.matches("[\\d\\s.,+-]+")) {
            throw new IllegalArgumentException("Ожидается непустой текст.");
        }
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Цена должна быть неотрицательной.");
        }
    }

    private void validatePositive(int value) {
        if (value <= 0) throw new IllegalArgumentException("Число должно быть больше нуля.");
    }
}
