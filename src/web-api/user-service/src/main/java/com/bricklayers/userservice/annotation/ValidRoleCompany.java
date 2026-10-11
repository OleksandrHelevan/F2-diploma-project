package com.bricklayers.userservice.annotation;

import com.bricklayers.userservice.validation.RoleCompanyConsistencyValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/*
  @author   user
  @project   F2-diploma-project
  @class  PasswordWithoutIdentity
  @version  1.0.0 
  @since 08.10.2026 - 18.29
*/

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RoleCompanyConsistencyValidator.class)
@Documented
public @interface ValidRoleCompany {
    String message() default "Роль і компанія вказані некоректно";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}