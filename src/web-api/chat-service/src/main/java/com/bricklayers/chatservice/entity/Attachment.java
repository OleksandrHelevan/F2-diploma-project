package com.bricklayers.chatservice.entity;

/*
  @author   user
  @project   F2-diploma-project
  @class  Attachment
  @version  1.0.0 
  @since 06.10.2026 - 19.24
*/
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Attachment {
    private String url;
    private String fileName;
    private String contentType;
    private long size;
}