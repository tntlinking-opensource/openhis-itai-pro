package com.openhis.whale.report.domain.support;

import com.openhis.whale.core.application.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportQueryValidatorTest {

    @Test
    void acceptsSimpleSelect() {
        assertDoesNotThrow(() -> ReportQueryValidator.validate("SELECT id, name FROM patient WHERE id = :id"));
    }

    @Test
    void acceptsWithClause() {
        assertDoesNotThrow(() -> ReportQueryValidator.validate(
                "WITH recent AS (SELECT id FROM visit WHERE tenant_id = :tenantId) SELECT * FROM recent"));
    }

    @Test
    void rejectsUnionBypass() {
        assertThrows(
                ValidationException.class,
                () -> ReportQueryValidator.validate("SELECT 1 UNION SELECT password FROM users"));
    }

    @Test
    void rejectsSemicolon() {
        assertThrows(
                ValidationException.class,
                () -> ReportQueryValidator.validate("SELECT 1; DROP TABLE patient"));
    }

    @Test
    void rejectsInsertKeywordInSelectBody() {
        assertThrows(
                ValidationException.class,
                () -> ReportQueryValidator.validate("SELECT * FROM patient WHERE 1=1 AND insert = true"));
    }

    @Test
    void rejectsForbiddenKeywordInBody() {
        assertThrows(
                ValidationException.class,
                () -> ReportQueryValidator.validate("SELECT * FROM patient WHERE pg_sleep(10) = 1"));
    }
}
