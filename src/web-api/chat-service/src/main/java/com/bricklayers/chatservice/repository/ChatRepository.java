package com.bricklayers.chatservice.repository;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatRepository
  @version  1.0.0 
  @since 06.10.2026 - 19.25
*/

import com.bricklayers.chatservice.entity.Chat;
import com.bricklayers.chatservice.entity.ChatType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRepository extends CrudRepository<Chat, String>, ChatCustomRepository {

    Optional<Chat> findByDirectKey(String directKey);

    Optional<Chat> findByProjectIdAndType(String projectId, ChatType type);

    Optional<Chat> findByBrigadeIdAndType(String brigadeId, ChatType type);

    List<Chat> findByBrigadeId(String brigadeId);

    @Query("""
            { 'companyId': ?0,
              'archived': false,
              'participants': { $elemMatch: { 'userId': ?1, 'leftAt': null } } }
            """)
    Page<Chat> findActiveChatsForUser(String companyId, String userId, Pageable pageable);

    @Query(value = """
            { '_id': ?0,
              'participants': { $elemMatch: { 'userId': ?1, 'leftAt': null } } }
            """, exists = true)
    boolean isActiveParticipant(String chatId, String userId);
}