package org.example.console;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * Ввод с консоли с повтором запроса при неверных данных.
 * @throw NumberFormatException
 */
@Component
public class ConsoleInput {

    // Читаем в кодировке консоли (cp866/cp1251 в Windows, UTF-8 в IDEA), иначе ломается кириллица
    private final Scanner scanner = new Scanner(System.in,
            Charset.forName(System.getProperty("stdout.encoding", "UTF-8"),Charset.defaultCharset()));

    /**
     * Выводит приглашение и читает строку без пробелов по краям.
     *
     * @throws InputClosedException если ввод закрыт
     */
    public String readLine(String prompt) {
        System.out.print(prompt);
        try {
            return scanner.nextLine().trim();
        } catch (NoSuchElementException e) {
            throw new InputClosedException();
        }
    }

    /** Текст: не пустой и не состоящий только из цифр. */
    public String readText(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (value.isEmpty()) {
                System.out.println("Ошибка: значение не может быть пустым.");
            } else if (value.matches("[\\d\\s.,+-]+")) {
                System.out.println("Ошибка: ожидается текст, а введены цифры.");
            } else {
                return value;
            }
        }
    }

    /** Читает целое число больше нуля, повторяя запрос при ошибке. */
    public int readPositiveInt(String prompt) {
        while (true) {
            String value = readLine(prompt);
            try {
                int number = Integer.parseInt(value);
                if (number <= 0) {
                    System.out.println("Ошибка: число должно быть больше нуля.");
                    continue;
                }
                return number;
            } catch (NumberFormatException e) {
                System.out.println("Ошибка формата: ожидается целое число, введено \"" + value + "\".");
            }
        }
    }

    /** Читает целое число, повторяя запрос при ошибке. */
    public long readLong(String prompt) {
        while (true) {
            String value = readLine(prompt);
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка формата: ожидается целое число, введено \"" + value + "\".");
            }
        }
    }

    /** Читает неотрицательную цену (допускается запятая), повторяя запрос при ошибке. */
    public BigDecimal readPrice(String prompt) {
        while (true) {
            String value = readLine(prompt);
            try {
                BigDecimal price = new BigDecimal(value.replace(',', '.'));
                if (price.signum() < 0) {
                    System.out.println("Ошибка: цена не может быть отрицательной.");
                    continue;
                }
                return price;
            } catch (NumberFormatException e) {
                System.out.println("Ошибка формата: ожидается число (например 199.99), введено \"" + value + "\".");
            }
        }
    }

    /** Ввод закончился (Ctrl+D / Ctrl+Z). */
    public static class InputClosedException extends RuntimeException {
    }
}
