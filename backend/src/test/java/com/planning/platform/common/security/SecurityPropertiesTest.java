package com.planning.platform.common.security;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void jwtSecretMustBeLongEnoughBeforeApplicationStarts() {
        SecurityProperties properties = new SecurityProperties();
        properties.setJwtSecret("short-secret");

        assertThat(validator.validate(properties))
                .anyMatch(violation -> "jwtSecret".equals(violation.getPropertyPath().toString()));
    }
}
