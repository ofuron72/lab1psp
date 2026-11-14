package org.example.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.console.ConsoleInput;
import org.example.exception.ItemNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;

@Component
@ConditionalOnProperty(name = "catalog.mode", havingValue = "udp-client")
public class UdpClientRunner implements CommandLineRunner {
    private final ConsoleInput input;
    private final UdpCatalogClient client;

    public UdpClientRunner(ConsoleInput input, ObjectMapper mapper,
                           @Value("${catalog.client.host:127.0.0.1}") String host,
                           @Value("${catalog.port:8888}") int port,
                           @Value("${catalog.udp.timeout-ms:3000}") int timeoutMillis) {
        this.input = input;
        client = new UdpCatalogClient(host, port, timeoutMillis, mapper);
    }

    @Override public void run(String... args) {
        System.out.println("UDP-клиент. Введите ID товара; 0 — выход.");
        try {
            while (true) {
                long id = input.readLong("ID товара: ");
                if (id == 0) return;
                if (id < 0) {
                    System.out.println("ID должен быть больше нуля.");
                    continue;
                }
                try {
                    System.out.println("UDP: отправка GET:" + id);
                    System.out.println(client.findById(id).displayInfo());
                } catch (ItemNotFoundException e) {
                    System.out.println(e.getMessage());
                } catch (SocketTimeoutException e) {
                    System.out.println("Ответ UDP-сервера не получен за отведенное время.");
                } catch (PortUnreachableException e) {
                    System.out.println("UDP-сервер недоступен.");
                } catch (IOException e) {
                    System.out.println("Ошибка UDP: " + e.getMessage());
                }
            }
        } catch (ConsoleInput.InputClosedException e) {
            System.out.println("Ввод завершён.");
        }
    }
}
