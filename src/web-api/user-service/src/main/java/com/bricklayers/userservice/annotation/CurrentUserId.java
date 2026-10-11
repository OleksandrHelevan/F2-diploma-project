package com.bricklayers.userservice.annotation;

import java.lang.annotation.*;

/*
  @author   user
  @project   F2-diploma-project
  @class  CurrentUserId
  @version  1.0.0 
  @since 08.10.2026 - 23.42
*/
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUserId { }
