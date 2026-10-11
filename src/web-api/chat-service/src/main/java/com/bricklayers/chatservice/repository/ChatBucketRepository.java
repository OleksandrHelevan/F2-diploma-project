package com.bricklayers.chatservice.repository;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatBucketRepository
  @version  1.0.0 
  @since 06.10.2026 - 19.27
*/

import com.bricklayers.chatservice.entity.ChatBucket;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface ChatBucketRepository extends CrudRepository<ChatBucket, String>, ChatBucketCustomRepository {

    Slice<ChatBucket> findByChatIdAndThreadIdIsNullOrderByEndDateDesc(String chatId, Pageable pageable);

    Optional<ChatBucket> findFirstByChatIdAndThreadIdIsNullOrderByEndDateDesc(String chatId);

    Slice<ChatBucket> findByChatIdAndThreadIdOrderByEndDateDesc(String chatId, String threadId, Pageable pageable);

    Optional<ChatBucket> findFirstByChatIdAndThreadIdOrderByEndDateDesc(String chatId, String threadId);

    void deleteByChatId(String chatId);
}