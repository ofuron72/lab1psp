package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Данные для создания книги. Подойдёт и как тело REST-запроса. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookRequest {

    private String name;
    private BigDecimal price;
    private String author;
    private int pageCount;
}
