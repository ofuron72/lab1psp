package org.example.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.exception.ItemNotFoundException;
import org.example.model.Item;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Блокирующий UDP-запрос с ограниченным временем ожидания ответа. */
public class UdpCatalogClient {
    private final InetSocketAddress server;
    private final int timeoutMillis;
    private final ObjectMapper mapper;

    public UdpCatalogClient(String host, int port, int timeoutMillis, ObjectMapper mapper) {
        if (timeoutMillis <= 0) throw new IllegalArgumentException("Тайм-аут должен быть положительным.");
        server = new InetSocketAddress(host, port);
        this.timeoutMillis = timeoutMillis;
        this.mapper = mapper;
    }

    public Item findById(long id) throws IOException {
        if (id <= 0) throw new IllegalArgumentException("ID должен быть больше нуля.");
        // Новый сокет для каждого запроса исключает прием запоздавшего ответа от прошлого запроса.
        try (var socket = new DatagramSocket()) {
            // Для UDP connect только задает адрес отправки и фильтрует отправителя ответа.
            // TCP-соединение и обмен рукопожатиями здесь не создаются.
            socket.connect(server);
            socket.setSoTimeout(timeoutMillis);
            byte[] request = ("GET:" + id).getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(request, request.length));
            var response = new DatagramPacket(new byte[65507], 65507);
            socket.receive(response);
            String json = new String(response.getData(), response.getOffset(), response.getLength(), StandardCharsets.UTF_8);
            var tree = mapper.readTree(json);
            if (tree == null || !tree.isObject()) throw new IOException("Сервер вернул неверный JSON-ответ.");
            if (tree.has("Error")) {
                String error = tree.path("Error").asText();
                if ("Not found".equals(error)) throw new ItemNotFoundException(id);
                throw new IOException("Ошибка сервера: " + error);
            }
            // Читаем исходный JSON, чтобы цена попадала в BigDecimal без промежуточного double.
            return mapper.readValue(json, Item.class);
        }
    }
}
