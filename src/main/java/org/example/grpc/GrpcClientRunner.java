package org.example.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.example.console.ConsoleInput;
import org.example.model.Book;
import org.example.model.Item;
import org.example.model.Journal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "catalog.mode", havingValue = "grpc-client")
public class GrpcClientRunner implements CommandLineRunner {
    private final ConsoleInput input;
    private final String host;
    private final int port;

    public GrpcClientRunner(ConsoleInput input,
                            @Value("${catalog.grpc.client-host:localhost}") String host,
                            @Value("${catalog.grpc.port:9090}") int port) {
        this.input = input;
        this.host = host;
        this.port = port;
    }

    @Override
    public void run(String... args) {
        try (var client = new GrpcCatalogClient(host, port)) {
            System.out.println("gRPC-клиент: " + host + ":" + port);
            while (true) {
                System.out.println("\n1. Добавить книгу\n2. Добавить журнал\n3. Все товары (stream)"
                        + "\n4. Найти по ID\n5. Редактировать товар\n6. Удалить товар\n0. Выйти");
                try {
                    switch (input.readLine("Выберите пункт: ")) {
                        case "1" -> show(client.add(readBook(null)));
                        case "2" -> show(client.add(readJournal(null)));
                        case "3" -> {
                            var items = client.getAll();
                            if (!items.hasNext()) System.out.println("Каталог пуст.");
                            while (items.hasNext()) show(items.next());
                        }
                        case "4" -> show(client.getById(input.readLong("ID товара: ")));
                        case "5" -> {
                            long id = input.readLong("ID товара: ");
                            Item item = client.getById(id);
                            show(item);
                            System.out.println("Введите новые значения всех полей:");
                            show(client.update(item instanceof Book ? readBook(id) : readJournal(id)));
                        }
                        case "6" -> {
                            client.delete(input.readLong("ID товара: "));
                            System.out.println("Товар удалён.");
                        }
                        case "0" -> { return; }
                        default -> System.out.println("Неизвестный пункт меню.");
                    }
                } catch (StatusRuntimeException e) {
                    if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                        System.out.println("Товар не найден.");
                    } else {
                        System.out.println("Ошибка gRPC " + e.getStatus().getCode() + ": " + e.getStatus().getDescription());
                    }
                }
            }
        } catch (ConsoleInput.InputClosedException e) {
            System.out.println("Ввод завершён.");
        }
    }

    private Book readBook(Long id) {
        return new Book(id, input.readText("Название: "), input.readPrice("Цена: "),
                input.readText("Автор: "), input.readPositiveInt("Количество страниц: "));
    }

    private Journal readJournal(Long id) {
        return new Journal(id, input.readText("Название: "), input.readPrice("Цена: "),
                input.readText("Издатель: "), input.readPositiveInt("Номер выпуска: "));
    }

    private void show(Item item) { System.out.println(item.displayInfo()); }
}
