package com.enterprise.chat.engine.repository;

import com.enterprise.chat.engine.model.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, String> {
    Optional<ChatMessageEntity> findBySenderIdAndClientMessageId(Long senderId, String clientMessageId);
    List<ChatMessageEntity> findByRoomIdAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            String roomId, Instant before, Pageable pageable);
    @Query("select m from ChatMessageEntity m where m.room.id = :roomId and " +
            "(m.createdAt < :before or (m.createdAt = :before and m.id < :beforeId)) " +
            "order by m.createdAt desc, m.id desc")
    List<ChatMessageEntity> findHistoryBefore(@Param("roomId") String roomId,
                                              @Param("before") Instant before,
                                              @Param("beforeId") String beforeId,
                                              Pageable pageable);
    List<ChatMessageEntity> findByRoomIdOrderByCreatedAtDescIdDesc(String roomId, Pageable pageable);
    Optional<ChatMessageEntity> findByIdAndRoomId(String id, String roomId);

    @Modifying
    @Transactional
    @Query("delete from ChatMessageEntity message where message.room.id = :roomId")
    void deleteAllByRoomId(@Param("roomId") String roomId);
}
