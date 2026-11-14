package org.example.console;

import lombok.RequiredArgsConstructor;
import org.example.dto.CreateBookRequest;
import org.example.dto.CreateJournalRequest;
import org.example.exception.CatalogFileCorruptedException;
import org.example.exception.CatalogFileWriteException;
import org.example.exception.ItemNotFoundException;
import org.example.model.Item;
import org.example.service.CatalogFileService;
import org.example.service.CatalogService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.util.List;

/**
 * Консольный интерфейс. Только ввод/вывод — логика в сервисах.
 * При добавлении web этот класс можно отключить (например, через @Profile("console")).
 */
@Component
@RequiredArgsConstructor
public class ConsoleMenu implements CommandLineRunner {

    private final CatalogService catalogService;
    private final CatalogFileService fileService;
    private final ConsoleInput input;

    /** Загружает каталог из файла и запускает меню. */
    @Override
    public void run(String... args) {
        loadCatalog();
        try {
            menuLoop();
        } catch (ConsoleInput.InputClosedException e) {
            System.out.println();
            System.out.println("Ввод завершён.");
            saveCatalog();
        }
    }

    /** Загружает каталог из файла; при отсутствии или повреждении файла каталог пуст. */
    private void loadCatalog() {
        try {
            List<Item> items = fileService.load();
            catalogService.replaceAll(items);
            System.out.println("Каталог загружен из " + fileService.getFileName() + ", товаров: " + items.size());
        } catch (FileNotFoundException e) {
            catalogService.replaceAll(List.of());
            System.out.println("Файл " + fileService.getFileName() + " не найден — создан пустой каталог.");
        } catch (CatalogFileCorruptedException e) {
            catalogService.replaceAll(List.of());
            System.out.println("Ошибка: " + e.getMessage());
            System.out.println("Каталог очищен.");
        }
    }

    /** Основной цикл меню до выхода пользователя. */
    private void menuLoop() {
        while (true) {
            printMenu();
            String choice = input.readLine("Выберите пункт: ");
            switch (choice) {
                case "1" -> addBook();
                case "2" -> addJournal();
                case "3" -> showAll();
                case "4" -> deleteItem();
                case "5" -> {
                    if (saveCatalog()) {
                        System.out.println("До свидания!");
                        return;
                    }
                    String answer = input.readLine("Выйти без сохранения? (y/n): ");
                    if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("д")) {
                        return;
                    }
                }
                default -> System.out.println("Неизвестный пункт меню: \"" + choice + "\"");
            }
        }
    }

    /** Печатает пункты меню. */
    private void printMenu() {
        System.out.println();
        System.out.println("===== Каталог товаров =====");
        System.out.println("1. Добавить книгу");
        System.out.println("2. Добавить журнал");
        System.out.println("3. Все товары");
        System.out.println("4. Удалить товар (по ID)");
        System.out.println("5. Сохранить и выйти");
    }

    /** Запрашивает данные и добавляет книгу. */
    private void addBook() {
        CreateBookRequest request = new CreateBookRequest(
                input.readText("Название: "),
                input.readPrice("Цена: "),
                input.readText("Автор: "),
                input.readPositiveInt("Количество страниц: "));
        Item book = catalogService.addBook(request);
        System.out.println("Книга добавлена, ID = " + book.getId());
    }

    /** Запрашивает данные и добавляет журнал. */
    private void addJournal() {
        CreateJournalRequest request = new CreateJournalRequest(
                input.readText("Название: "),
                input.readPrice("Цена: "),
                input.readText("Издатель: "),
                input.readPositiveInt("Номер выпуска: "));
        Item journal = catalogService.addJournal(request);
        System.out.println("Журнал добавлен, ID = " + journal.getId());
    }

    /** Выводит все товары каталога. */
    private void showAll() {
        List<Item> items = catalogService.getAll();
        if (items.isEmpty()) {
            System.out.println("Каталог пуст.");
            return;
        }
        items.forEach(item -> System.out.println(item.displayInfo()));
    }

    /** Запрашивает ID и удаляет товар. */
    private void deleteItem() {
        long id = input.readLong("ID товара для удаления: ");
        try {
            catalogService.deleteById(id);
            System.out.println("Товар с ID " + id + " удалён.");
        } catch (ItemNotFoundException e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
    }

    /**
     * Сохраняет каталог в файл.
     *
     * @return {@code true}, если сохранение прошло успешно
     */
    private boolean saveCatalog() {
        try {
            fileService.save(catalogService.getAll());
            System.out.println("Каталог сохранён в " + fileService.getFileName());
            return true;
        } catch (CatalogFileWriteException e) {
            System.out.println("Ошибка: " + e.getMessage());
            return false;
        }
    }
}
