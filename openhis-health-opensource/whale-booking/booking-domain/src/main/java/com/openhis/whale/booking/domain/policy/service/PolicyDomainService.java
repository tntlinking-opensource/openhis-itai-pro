package com.openhis.whale.booking.domain.policy.service;

import com.openhis.whale.booking.domain.policy.entity.RegistrationPolicy;
import com.openhis.whale.booking.domain.policy.entity.ReleasePolicy;
import com.openhis.whale.booking.domain.policy.entity.ScheduleChangePolicy;
import com.openhis.whale.booking.domain.policy.repository.RegistrationPolicyRepository;
import com.openhis.whale.booking.domain.policy.repository.ReleasePolicyRepository;
import com.openhis.whale.booking.domain.policy.repository.ScheduleChangePolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PolicyDomainService {

    private final ReleasePolicyRepository releasePolicyRepository;
    private final RegistrationPolicyRepository registrationPolicyRepository;
    private final ScheduleChangePolicyRepository scheduleChangePolicyRepository;

    public ReleasePolicy saveReleasePolicy(ReleasePolicy policy, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        if (policy.getId() == null) {
            policy.setCreatedAt(now);
            policy.setCreatedBy(operatorId);
            policy.setIsDeleted(Boolean.FALSE);
        }
        policy.setUpdatedAt(now);
        policy.setUpdatedBy(operatorId);
        return releasePolicyRepository.save(policy);
    }

    public void deleteReleasePolicy(Long id, Long operatorId) {
        releasePolicyRepository.findById(id).ifPresent(policy -> {
            policy.setIsDeleted(Boolean.TRUE);
            policy.setDeletedAt(LocalDateTime.now());
            policy.setDeletedBy(operatorId);
            releasePolicyRepository.save(policy);
        });
    }

    public RegistrationPolicy saveRegistrationPolicy(RegistrationPolicy policy, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        if (policy.getId() == null) {
            policy.setCreatedAt(now);
            policy.setCreatedBy(operatorId);
            policy.setIsDeleted(Boolean.FALSE);
        }
        policy.setUpdatedAt(now);
        policy.setUpdatedBy(operatorId);
        return registrationPolicyRepository.save(policy);
    }

    public void deleteRegistrationPolicy(Long id, Long operatorId) {
        registrationPolicyRepository.findById(id).ifPresent(policy -> {
            policy.setIsDeleted(Boolean.TRUE);
            policy.setDeletedAt(LocalDateTime.now());
            policy.setDeletedBy(operatorId);
            registrationPolicyRepository.save(policy);
        });
    }

    /**
     * 保存科室停诊变更策略；同科室仅保留一条有效记录。
     */
    public ScheduleChangePolicy saveScheduleChangePolicy(ScheduleChangePolicy policy, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        if (policy.getOrganizationId() != null && policy.getId() == null) {
            scheduleChangePolicyRepository.findFirstByOrganizationIdAndIsDeletedFalse(policy.getOrganizationId())
                .ifPresent(existing -> policy.setId(existing.getId()));
        }
        if (policy.getId() == null) {
            policy.setCreatedAt(now);
            policy.setCreatedBy(operatorId);
            policy.setIsDeleted(Boolean.FALSE);
        }
        policy.setUpdatedAt(now);
        policy.setUpdatedBy(operatorId);
        return scheduleChangePolicyRepository.save(policy);
    }
}
