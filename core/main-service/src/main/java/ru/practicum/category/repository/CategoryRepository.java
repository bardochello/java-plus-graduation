package ru.practicum.category.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.category.model.Category;

import java.util.Collection;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Находит категорию по имени (без учета регистра).
     *
     * @param name имя категории для поиска
     * @return категория, если найдена
     */
    Optional<Category> findByNameContainingIgnoreCase(String name);

    /**
     * Находит категорию по имени, исключая указанные идентификаторы.
     *
     * @param name имя категории для поиска
     * @param id   идентификаторы для исключения
     * @return категория, если найдена
     */
    Optional<Category> findByNameContainingIgnoreCaseAndIdNotIn(String name, Collection<Long> id);
}