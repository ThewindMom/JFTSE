package com.jftse.entities.database.repository.item;

import com.jftse.entities.database.model.item.ItemCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemCardRepository extends JpaRepository<ItemCard, Long> {
    Optional<ItemCard> findByItemIndex(Integer itemIndex);
    List<ItemCard> findAllByItemIndexIn(List<Integer> itemIndexes);
}
