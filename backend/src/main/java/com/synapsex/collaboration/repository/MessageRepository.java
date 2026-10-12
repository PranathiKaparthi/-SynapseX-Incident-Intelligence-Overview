package com.synapsex.collaboration.repository;

import com.synapsex.collaboration.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    Optional<Message> findTopByRoomIdOrderBySeqNumDesc(UUID roomId);

    Optional<Message> findByRoomIdAndClientMessageId(UUID roomId, String clientMessageId);

    List<Message> findByRoomIdAndSeqNumGreaterThanOrderBySeqNumAsc(UUID roomId, long sinceSeq);

    List<Message> findByRoomIdOrderBySeqNumAsc(UUID roomId);

    Page<Message> findByRoomIdOrderBySeqNumAsc(UUID roomId, Pageable pageable);
}
