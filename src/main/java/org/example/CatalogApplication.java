package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CatalogApplication {

    /** Точка входа: запускает Spring-контекст и консольное меню. */
    public static void main(String[] args) {
        var context = SpringApplication.run(CatalogApplication.class, args);
        // После выхода из консольного меню закрываем также пул асинхронных задач.
        context.close();
    }
}
