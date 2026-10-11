package com.bricklayers.userservice.validation;

/*
  @author   user
  @project   F2-diploma-project
  @class  RoleCompanyConsistencyValidatorTest
  @version  1.0.0 
  @since 08.10.2026 - 22.14
*/

import com.bricklayers.userservice.dto.CreateUserRequest;
import com.bricklayers.userservice.entity.Role;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


class RoleCompanyConsistencyValidatorTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private CreateUserRequest req(Role role, UUID companyId) {
        return new CreateUserRequest("oleksandr", "oleks@ex.com", "Oleksandr", "Helevan", role, companyId);
    }

    @Test
    void validWhenSuperAdminHasNoCompany() {
        assertThat(validator.validate(req(Role.SUPERADMIN, null))).isEmpty();
    }

    @Test
    void validWhenOtherRoleHasCompany() {
        assertThat(validator.validate(req(Role.BUILDER, UUID.randomUUID()))).isEmpty();
    }

    @Test
    void violationWhenSuperAdminHasCompany() {
        var violations = validator.validate(req(Role.SUPERADMIN, UUID.randomUUID()));
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("SUPERADMIN не може бути прив'язаний до компанії");
    }

    @Test
    void violationWhenOtherRoleHasNoCompany() {
        var violations = validator.validate(req(Role.BUILDER, null));
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("потрібно вказати companyId");
    }
}