package com.jftse.entities.database.repository.pocket;

import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlayerPocketRepository extends JpaRepository<PlayerPocket, Long> {
    Optional<PlayerPocket> findById(Long id);
    Optional<PlayerPocket> findByItemIndex(Integer itemIndex);
    Optional<PlayerPocket> findByIdAndPocket(Long id, Pocket pocket);
    Optional<PlayerPocket> findByIdAndPocketId(Long id, Long pocketId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pp FROM PlayerPocket pp JOIN FETCH pp.pocket WHERE pp.id = :id AND pp.pocket.id = :pocketId")
    Optional<PlayerPocket> findByIdAndPocketIdForUpdate(@Param("id") Long id, @Param("pocketId") Long pocketId);
    List<PlayerPocket> findAllByPocketAndIdIn(Pocket pocket, List<Long> ids);
    List<PlayerPocket> findAllByPocketAndItemIndexIn(Pocket pocket, List<Integer> itemIndexList);
    Optional<PlayerPocket> findByItemIndexAndPocket(Integer itemIndex, Pocket pocket);
    List<PlayerPocket> findAllByItemIndexAndPocket(Integer itemIndex, Pocket pocket);
    Optional<PlayerPocket> findByItemIndexAndCategoryAndPocket(Integer itemIndex, String category, Pocket pocket);
    List<PlayerPocket> findAllByItemIndexAndCategoryAndPocket(Integer itemIndex, String category, Pocket pocket);
    List<PlayerPocket> findAllByItemIndexAndCategoryAndPocketId(Integer itemIndex, String category, Long pocketId);
    List<PlayerPocket> findAllByPocketAndCategoryAndItemIndexIn(Pocket pocket, String category, List<Integer> itemIndexList);
    List<PlayerPocket> findAllByPocket(Pocket pocket);
    List<PlayerPocket> findAllByPocketAndCategory(Pocket pocket, String category);
}
