package com.openhis.whale.einvoice.boss.domain.service;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.einvoice.boss.contracts.constants.EinvBossErrorCodes;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link BossInvoiceAmountValidator} 单元测试。
 */
class BossInvoiceAmountValidatorTest {

    private final BossInvoiceAmountValidator validator = new BossInvoiceAmountValidator();

    @Test
    void validate_equalAmounts_passes() {
        assertDoesNotThrow(() -> validator.validate(
                new BigDecimal("100.00"),
                List.of(new BigDecimal("60.00"), new BigDecimal("40.00"))));
    }

    @Test
    void validate_oneCentDiff_throws() {
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate(
                new BigDecimal("100.00"),
                List.of(new BigDecimal("99.99"))));
        assertEquals(EinvBossErrorCodes.EINV_AMOUNT_MISMATCH, ex.getErrorCode());
    }
}
