package org.example.repository.jpa;

import org.example.model.Item;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

/** CRUD-запросы формирует Spring Data JPA; значения передаются параметрами. */
@Profile("db")
public interface JpaItemRepository extends JpaRepository<Item, Long> {
}
