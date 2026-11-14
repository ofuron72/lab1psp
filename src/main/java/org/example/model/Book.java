package org.example.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class Book extends Item {

    private String author;
    private int pageCount;

    public Book(Long id, String name, BigDecimal price, String author, int pageCount) {
        super(id, name, price);
        this.author = author;
        this.pageCount = pageCount;
    }

    @Override
    public String displayInfo() {
        return String.format("[Книга]  ID: %d | Название: %s | Цена: %s | Автор: %s | Страниц: %d",
                getId(), getName(), getPrice(), author, pageCount);
    }
}
