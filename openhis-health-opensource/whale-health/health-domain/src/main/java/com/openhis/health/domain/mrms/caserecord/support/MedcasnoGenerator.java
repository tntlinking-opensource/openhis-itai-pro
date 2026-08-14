/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.support;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 病案号发号器（Whale B.10.9 业务流水）：{@code CR} + {@code yyyy-MM-dd} + 4 位日流水。
 *
 * <p>与住院号 {@code ZY} 同构，走 {@link BusinessSequenceService#getSeqByDay}；租户隔离、跨日从 1 起。</p>
 */
@Service
@RequiredArgsConstructor
public class MedcasnoGenerator {

    private final BusinessSequenceService businessSequenceService;

    /**
     * 分配租户内唯一病案号（前缀 {@link AggregateTypeConstants#CASE_RECORD_MEDCASNO}，按自然日流水）。
     *
     * @param tenantId 租户主键（须非空；实际隔离依赖会话租户上下文）
     * @return 病案号，如 {@code CR2026-08-130001}
     */
    @Transactional(rollbackFor = Exception.class)
    public String nextMedcasno(Long tenantId) {
        // Gate：租户空则拒绝发号，避免无隔离上下文撞号
        if (tenantId == null) {
            throw new BusinessException(MrmsErrorCodes.MEDCASNO_GENERATE_FAILED, "租户不能为空");
        }
        // Cmd：按日业务流水（前缀 + 年月日 + 4 位序号）
        return businessSequenceService.getSeqByDay(
                AggregateTypeConstants.CASE_RECORD_MEDCASNO,
                BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
    }
}
