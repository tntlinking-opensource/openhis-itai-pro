package com.openhis.whale.mrms.domain.service;

import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.mrms.domain.entity.MedcasnoSequence;
import com.openhis.whale.mrms.domain.repository.MedcasnoSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;

/**
 * 病案号发号：{yyyy}{5位流水}。
 */
@Service
@RequiredArgsConstructor
public class MedcasnoGenerator {

    private final MedcasnoSequenceRepository sequenceRepository;

    @Transactional(rollbackFor = Exception.class)
    public String nextMedcasno() {
        Long tenantId = TenantUtils.getCurrentTenantId();
        int yearNo = Year.now().getValue();
        MedcasnoSequence sequence = sequenceRepository.findForUpdate(tenantId, yearNo)
                .orElseGet(() -> createSequence(tenantId, yearNo));
        long next = sequence.getLastValue() + 1;
        sequence.setLastValue(next);
        sequence.setUpdatedAt(LocalDateTime.now());
        sequenceRepository.save(sequence);
        return String.format("%d%05d", yearNo, next);
    }

    private MedcasnoSequence createSequence(Long tenantId, int yearNo) {
        MedcasnoSequence sequence = new MedcasnoSequence();
        sequence.setTenantId(tenantId);
        sequence.setYearNo(yearNo);
        sequence.setLastValue(0L);
        sequence.setCreatedAt(LocalDateTime.now());
        return sequenceRepository.save(sequence);
    }
}
