package com.bricklayers.userservice.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.annotation.*;

/*
  @author   user
  @project   F2-diploma-project
  @class  SuperAdminDelete
  @version  1.0.0 
  @since 08.10.2026 - 22.25
*/

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RequestMapping(method = RequestMethod.DELETE)
@ResponseStatus(HttpStatus.NO_CONTENT)
@PreAuthorize("hasRole('SUPERADMIN')")
public @interface SuperAdminDelete {

    @AliasFor(annotation = RequestMapping.class, attribute = "value")
    String[] value() default {};
}