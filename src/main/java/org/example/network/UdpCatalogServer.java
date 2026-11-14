package org.example.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.exception.ItemNotFoundException;
import org.example.service.CatalogService;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** UDP-сервер поиска: одна датаграмма GET:ID — один JSON-ответ. */
public class UdpCatalogServer implements AutoCloseable {
    private final DatagramSocket socket;
    private final CatalogService catalog;
    private final ObjectMapper mapper;

    public UdpCatalogServer(String host, int port, CatalogService catalog, ObjectMapper mapper) throws IOException {
        this.catalog = catalog;
        this.mapper = mapper;
        socket = new DatagramSocket(new InetSocketAddress(host, port));
    }

    public int port() { return socket.getLocalPort(); }

    public void serve() throws IOException {
        System.out.println("UDP-сервер: " + socket.getLocalSocketAddress());
        while (!socket.isClosed()) {
            var request = new DatagramPacket(new byte[65507], 65507);
            try {
                socket.receive(request);
                String command = new String(request.getData(), request.getOffset(), request.getLength(), StandardCharsets.UTF_8);
                System.out.println("UDP " + request.getSocketAddress() + " -> " + command);
                byte[] response = reply(command);
                socket.send(new DatagramPacket(response, response.length, request.getSocketAddress()));
            } catch (SocketException e) {
                if (socket.isClosed()) return;
                throw e;
            } catch (IOException e) {
                if (!socket.isClosed()) System.out.println("Ошибка UDP: " + e.getMessage());
            }
        }
    }

    private byte[] reply(String command) throws IOException {
        if (!command.matches("GET:[1-9][0-9]*")) return error("Bad request");
        try {
            long id = Long.parseLong(command.substring(4));
            byte[] json = mapper.writerFor(org.example.model.Item.class).writeValueAsBytes(catalog.getById(id));
            return json.length <= 65507 ? json : error("Response too large");
        } catch (NumberFormatException e) {
            return error("Bad request");
        } catch (ItemNotFoundException e) {
            return error("Not found");
        }
    }

    private byte[] error(String message) throws IOException {
        return mapper.writeValueAsBytes(Map.of("Error", message));
    }

    @Override public void close() { socket.close(); }
}
