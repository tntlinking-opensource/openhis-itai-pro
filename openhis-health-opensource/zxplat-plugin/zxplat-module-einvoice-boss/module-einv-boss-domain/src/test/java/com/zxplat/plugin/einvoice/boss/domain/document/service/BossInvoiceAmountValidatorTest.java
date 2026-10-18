/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.document.service;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.plugin.einvoice.boss.domain.share.EinvBossErrorCodes;
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
