package org.example.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.console.ConsoleInput;
import org.example.dto.CreateBookRequest;
import org.example.dto.CreateJournalRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "catalog.mode", havingValue = "client")
public class TcpClientRunner implements CommandLineRunner {
    private final ConsoleInput input;
    private final ObjectMapper mapper;
    private final String host;
    private final int port;

    public TcpClientRunner(ConsoleInput input, ObjectMapper mapper,
                           @Value("${catalog.client.host:localhost}") String host,
                           @Value("${catalog.port:8888}") int port) {
        this.input = input;
        this.mapper = mapper;
        this.host = host;
        this.port = port;
    }

    @Override
    public void run(String... args) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            try (var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 var out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                System.out.println("Подключено к " + host + ":" + port);
                while (true) {
                    System.out.println("\n1. Добавить книгу\n2. Добавить журнал\n3. Все товары"
                            + "\n4. Удалить товар (по ID)\n5. Сохранить и выйти");
                    CatalogRequest request = readRequest();
                    if (request == null) continue;
                    out.write(mapper.writeValueAsString(request));
                    out.newLine();
                    out.flush();
                    System.out.println("Ожидание ответа сервера...");
                    String line = in.readLine();
                    if (line == null) throw new EOFException("Сервер закрыл соединение.");
                    CatalogResponse response = mapper.readValue(line, CatalogResponse.class);
                    System.out.println((response.success() ? "" : "Ошибка: ") + response.message());
                    if ("LIST".equals(request.command()) && response.success()) {
                        if (response.items().isEmpty()) System.out.println("Каталог пуст.");
                        response.items().forEach(item -> System.out.println(item.displayInfo()));
                    }
                    if ("QUIT".equals(request.command()) && response.success()) return;
                }
            }
        } catch (ConsoleInput.InputClosedException e) {
            System.out.println("Ввод завершён, соединение закрыто.");
        } catch (IOException e) {
            System.out.println("Ошибка соединения: " + e.getMessage());
        }
    }

    private CatalogRequest readRequest() {
        return switch (input.readLine("Выберите пункт: ")) {
            case "1" -> new CatalogRequest("ADD_BOOK", new CreateBookRequest(
                    input.readText("Название: "), input.readPrice("Цена: "),
                    input.readText("Автор: "), input.readPositiveInt("Количество страниц: ")), null, null);
            case "2" -> new CatalogRequest("ADD_JOURNAL", null, new CreateJournalRequest(
                    input.readText("Название: "), input.readPrice("Цена: "),
                    input.readText("Издатель: "), input.readPositiveInt("Номер выпуска: ")), null);
            case "3" -> CatalogRequest.command("LIST");
            case "4" -> new CatalogRequest("DELETE", null, null, input.readLong("ID товара: "));
            case "5" -> CatalogRequest.command("QUIT");
            default -> {
                System.out.println("Неизвестный пункт меню.");
                yield null;
            }
        };
    }
}
