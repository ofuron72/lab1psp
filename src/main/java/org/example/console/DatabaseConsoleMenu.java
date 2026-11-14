package org.example.console;

import lombok.RequiredArgsConstructor;
import org.example.dto.CreateBookRequest;
import org.example.dto.CreateJournalRequest;
import org.example.exception.ItemNotFoundException;
import org.example.model.Book;
import org.example.model.Item;
import org.example.service.AsyncJpaCatalogService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.core.task.TaskRejectedException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Меню ЛР №4. Запросы к репозиторию выполняются только через сервис. */
@Component
@Profile("db")
@ConditionalOnProperty(name = "catalog.mode", havingValue = "db")
@RequiredArgsConstructor
public class DatabaseConsoleMenu implements CommandLineRunner {
    private final AsyncJpaCatalogService catalog;
    private final ConsoleInput input;

    @Override
    public void run(String... args) {
        System.out.println("Каталог PostgreSQL. Изменения сохраняются после каждой операции.");
        try {
            while (true) {
                System.out.println("\n1. Добавить книгу\n2. Добавить журнал\n3. Все товары"
                        + "\n4. Найти по ID\n5. Редактировать товар\n6. Удалить товар\n0. Выйти");
                try {
                    switch (input.readLine("Выберите пункт: ")) {
                        case "1" -> show(await(catalog.addBook(readBook())));
                        case "2" -> show(await(catalog.addJournal(readJournal())));
                        case "3" -> {
                            var items = await(catalog.getAll());
                            if (items.isEmpty()) System.out.println("Каталог пуст.");
                            items.forEach(this::show);
                        }
                        case "4" -> show(await(catalog.getById(input.readLong("ID товара: "))));
                        case "5" -> edit();
                        case "6" -> {
                            await(catalog.deleteById(input.readLong("ID товара: ")));
                            System.out.println("Товар удалён.");
                        }
                        case "0" -> { return; }
                        default -> System.out.println("Неизвестный пункт меню.");
                    }
                } catch (IllegalArgumentException | ItemNotFoundException e) {
                    System.out.println("Ошибка: " + e.getMessage());
                } catch (DataAccessException e) {
                    System.out.println("Ошибка базы данных: операция не выполнена.");
                } catch (TaskRejectedException e) {
                    System.out.println("Сервис занят, повторите операцию позже.");
                }
            }
        } catch (ConsoleInput.InputClosedException e) {
            System.out.println("Ввод завершён.");
        }
    }

    private void edit() {
        long id = input.readLong("ID товара: ");
        Item item = await(catalog.getById(id));
        show(item);
        System.out.println("Введите новые значения всех полей:");
        if (item instanceof Book) {
            show(await(catalog.updateBook(id, readBook())));
        } else {
            show(await(catalog.updateJournal(id, readJournal())));
        }
    }

    private CreateBookRequest readBook() {
        return new CreateBookRequest(input.readText("Название: "), input.readPrice("Цена: "),
                input.readText("Автор: "), input.readPositiveInt("Количество страниц: "));
    }

    private CreateJournalRequest readJournal() {
        return new CreateJournalRequest(input.readText("Название: "), input.readPrice("Цена: "),
                input.readText("Издатель: "), input.readPositiveInt("Номер выпуска: "));
    }

    /** Меню последовательное: ждет результат, сохраняя исходный тип ошибки сервиса. */
    private <T> T await(CompletableFuture<T> result) {
        try {
            return result.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) throw cause;
            throw new IllegalStateException("Не удалось выполнить операцию.", e.getCause());
        }
    }

    private void show(Item item) { System.out.println(item.displayInfo()); }
}
