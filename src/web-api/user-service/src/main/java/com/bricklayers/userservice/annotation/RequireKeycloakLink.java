package com.bricklayers.userservice.annotation;

import java.lang.annotation.*;

/*
  @author   oleksandr
  @project   F2-diploma-project
  @class  RequireKeycloakLink
  @version  1.0.0
  @since 22.10.2026 - 12.00
*/

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireKeycloakLink { }
