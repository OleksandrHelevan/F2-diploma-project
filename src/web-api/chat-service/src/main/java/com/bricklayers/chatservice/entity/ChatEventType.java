package com.bricklayers.chatservice.entity;

/*
  @author   user
  @project   F2-diploma-project
  @class  MessageEvent
  @version  1.0.0 
  @since 06.10.2026 - 19.11
*/

public enum ChatEventType {
    NEW_MESSAGE,
    TYPING, STOPPED_TYPING, STATUS_UPDATE, ERROR, LIKE_UPDATE,
    MESSAGE_EDITED, MESSAGE_DELETED, THREAD_REPLY,
    PARTICIPANT_ADDED, PARTICIPANT_REMOVED
}