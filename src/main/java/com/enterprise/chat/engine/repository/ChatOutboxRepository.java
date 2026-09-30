package com.enterprise.chat.engine.repository;

import com.enterprise.chat.engine.model.ChatOutboxEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface ChatOutboxRepository extends JpaRepository<ChatOutboxEntity, Long> {
    @Query(value = "SELECT * FROM chat_outbox WHERE published_at IS NULL ORDER BY id LIMIT 50 FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<ChatOutboxEntity> lockPending();

    @Modifying
    @Transactional
    @Query("delete from ChatOutboxEntity outbox where outbox.payload like concat('%', :roomId, '%')")
    void deleteRoomEvents(@Param("roomId") String roomId);
}
