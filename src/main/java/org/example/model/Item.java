package org.example.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Базовый товар каталога. Поле "type" в JSON определяет конкретный подкласс.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Book.class, name = "book"),
        @JsonSubTypes.Type(value = Journal.class, name = "journal")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Item {

    private Long id;
    private String name;
    private BigDecimal price;

    /**
     * Текстовое представление товара. Возвращает строку, а не печатает сама,
     * чтобы метод можно было использовать не только в консоли.
     */
    public abstract String displayInfo();
}
