package org.example.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@DiscriminatorValue("journal")
public class Journal extends Item {

    @Column(length = 255)
    private String publisher;
    @Column(name = "issue_number")
    private int issueNumber;

    public Journal(Long id, String name, BigDecimal price, String publisher, int issueNumber) {
        super(id, name, price);
        this.publisher = publisher;
        this.issueNumber = issueNumber;
    }

    @Override
    public String displayInfo() {
        return String.format("[Журнал] ID: %d | Название: %s | Цена: %s | Издатель: %s | Выпуск №%d",
                getId(), getName(), getPrice(), publisher, issueNumber);
    }
}
