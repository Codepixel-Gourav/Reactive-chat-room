package com.enterprise.chat.engine.repository;

import com.enterprise.chat.engine.model.RoomMembershipEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public interface RoomMembershipRepository extends JpaRepository<RoomMembershipEntity, Long> {
    boolean existsByRoomIdAndUserId(String roomId, Long userId);
    Optional<RoomMembershipEntity> findByRoomIdAndUserId(String roomId, Long userId);
    List<RoomMembershipEntity> findAllByUserId(Long userId);
    List<RoomMembershipEntity> findAllByRoomId(String roomId);
    long countByRoomId(String roomId);

    @Modifying
    @Transactional
    @Query("delete from RoomMembershipEntity membership where membership.room.id = :roomId and membership.user.id = :userId")
    void deleteByRoomIdAndUserId(@Param("roomId") String roomId, @Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("delete from RoomMembershipEntity membership where membership.room.id = :roomId")
    void deleteByRoomId(@Param("roomId") String roomId);
}
