package com.enterprise.chat.engine.repository;

import com.enterprise.chat.engine.model.MessageReceiptEntity;
import com.enterprise.chat.engine.model.ReceiptState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface MessageReceiptRepository extends JpaRepository<MessageReceiptEntity, Long> {
    Optional<MessageReceiptEntity> findByMessageIdAndUserId(String messageId, Long userId);

    @Query("select receipt.message.id as messageId, receipt.state as state " +
            "from MessageReceiptEntity receipt where receipt.message.id in :messageIds")
    List<ReceiptSummary> findReceiptStates(@Param("messageIds") List<String> messageIds);

    interface ReceiptSummary {
        String getMessageId();
        ReceiptState getState();
    }

    @Modifying
    @Transactional
    @Query("delete from MessageReceiptEntity receipt where receipt.message.id = :messageId")
    void deleteAllByMessageId(@Param("messageId") String messageId);

    @Modifying
    @Transactional
    @Query("delete from MessageReceiptEntity receipt where receipt.message.room.id = :roomId")
    void deleteAllByRoomId(@Param("roomId") String roomId);
}
