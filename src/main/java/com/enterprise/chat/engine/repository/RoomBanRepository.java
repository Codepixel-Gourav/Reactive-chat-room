package com.enterprise.chat.engine.repository;

import com.enterprise.chat.engine.model.RoomBanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RoomBanRepository extends JpaRepository<RoomBanEntity, Long> {
    boolean existsByRoomIdAndUserId(String roomId, Long userId);

    @Modifying
    @Transactional
    @Query("delete from RoomBanEntity ban where ban.room.id = :roomId")
    void deleteByRoomId(@Param("roomId") String roomId);
}
