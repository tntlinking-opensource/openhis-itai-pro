/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.support;

import com.openhis.health.domain.mrms.caserecord.entity.MedcasnoSequence;
import com.openhis.health.domain.mrms.caserecord.repository.MedcasnoSequenceRepository;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;

/**
 * 病案号发号器：格式 {@code {yyyy}{5位流水}}，按租户+自然年隔离序号。
 */
@Service
@RequiredArgsConstructor
public class MedcasnoGenerator {

    private final MedcasnoSequenceRepository medcasnoSequenceRepository;

    /**
     * 分配租户内唯一病案号；依赖 {@code mrms_medcasno_sequence} 行锁保障并发安全。
     *
     * @param tenantId 租户主键
     * @return 病案号字符串
     */
    @Transactional(rollbackFor = Exception.class)
    public String nextMedcasno(Long tenantId) {
        if (tenantId == null) {
            throw new BusinessException(MrmsErrorCodes.MEDCASNO_GENERATE_FAILED, "租户不能为空");
        }
        int year = Year.now().getValue();
        MedcasnoSequence sequence = medcasnoSequenceRepository
                .findByTenantIdAndYearValueAndIsDeletedFalse(tenantId, year)
                .orElse(null);
        // 当年首号：初始化序列表
        if (sequence == null) {
            sequence = new MedcasnoSequence();
            sequence.setTenantId(tenantId);
            sequence.setYearValue(year);
            sequence.setLastSeq(0);
            sequence.setCreatedAt(LocalDateTime.now());
            sequence.setIsDeleted(false);
        }
        int nextSeq = sequence.getLastSeq() + 1;
        sequence.setLastSeq(nextSeq);
        sequence.setUpdatedAt(LocalDateTime.now());
        // 持久化序号（行锁保障并发）
        medcasnoSequenceRepository.save(sequence);
        String seqPart = String.format("%05d", nextSeq);
        return year + seqPart;
    }
}
