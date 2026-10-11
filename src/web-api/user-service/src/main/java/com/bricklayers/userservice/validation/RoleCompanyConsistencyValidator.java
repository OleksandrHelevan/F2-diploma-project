package com.bricklayers.userservice.validation;

import com.bricklayers.userservice.annotation.ValidRoleCompany;
import com.bricklayers.userservice.dto.CreateUserRequest;
import com.bricklayers.userservice.entity.Role;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/*
  @author   user
  @project   F2-diploma-project
  @class  PasswordWithoutIdentityValidator
  @version  1.0.0 
  @since 08.10.2026 - 18.30
*/

public class RoleCompanyConsistencyValidator
        implements ConstraintValidator<ValidRoleCompany, CreateUserRequest> {

    @Override
    public boolean isValid(CreateUserRequest req, ConstraintValidatorContext ctx) {
        if (req.role() == null) {
            return true;
        }
        ctx.disableDefaultConstraintViolation();

        if (req.role() == Role.SUPERADMIN && req.companyId() != null) {
            ctx.buildConstraintViolationWithTemplate(
                            "SUPERADMIN не може бути прив'язаний до компанії")
                    .addPropertyNode("companyId").addConstraintViolation();
            return false;
        }
        if (req.role() != Role.SUPERADMIN && req.companyId() == null) {
            ctx.buildConstraintViolationWithTemplate(
                            "Для ролі " + req.role().name() + " потрібно вказати companyId")
                    .addPropertyNode("companyId").addConstraintViolation();
            return false;
        }
        return true;
    }
}