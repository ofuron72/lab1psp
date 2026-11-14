package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Данные для создания журнала. Подойдёт и как тело REST-запроса. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateJournalRequest {

    private String name;
    private BigDecimal price;
    private String publisher;
    private int issueNumber;
}
