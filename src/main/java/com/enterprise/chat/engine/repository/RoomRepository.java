package com.enterprise.chat.engine.repository;

import com.enterprise.chat.engine.model.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<RoomEntity, String> {
    Optional<RoomEntity> findByNameIgnoreCase(String name);

    @Query("""
            select room from RoomEntity room
            where not exists (
                select membership.id from RoomMembershipEntity membership
                where membership.room = room and membership.user.id = :userId
            )
            order by room.name
            """)
    List<RoomEntity> findPublicRoomsForUser(@Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from RoomEntity room where room.id = :roomId")
    void deleteRoomById(@Param("roomId") String roomId);
}
