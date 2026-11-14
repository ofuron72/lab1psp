package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CatalogApplication {

    /** Точка входа: запускает Spring-контекст и консольное меню. */
    public static void main(String[] args) {
        var context = SpringApplication.run(CatalogApplication.class, args);
        // После завершения консольного режима освобождаем ресурсы Spring.
        context.close();
    }
}
