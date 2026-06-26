/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.service;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.mrms.domain.entity.MedcasnoSequence;
import com.openhis.whale.mrms.domain.repository.MedcasnoSequenceRepository;
import com.openhis.whale.mrms.domain.support.MrmsErrorCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;

/**
 * 病案号发号器：格式 {@code {yyyy}{5位流水}}。
 */
@Service
@RequiredArgsConstructor
public class MedcasnoGenerator {

    private final MedcasnoSequenceRepository medcasnoSequenceRepository;

    /**
     * 分配租户内唯一病案号。
     *
     * @param tenantId 租户主键
     * @return 病案号
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
        medcasnoSequenceRepository.save(sequence);
        String seqPart = String.format("%05d", nextSeq);
        return year + seqPart;
    }
}
