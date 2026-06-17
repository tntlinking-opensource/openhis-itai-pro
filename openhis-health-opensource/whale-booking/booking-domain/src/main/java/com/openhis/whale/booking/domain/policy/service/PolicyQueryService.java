package com.openhis.whale.booking.domain.policy.service;

import com.openhis.whale.booking.domain.policy.entity.RegistrationPolicy;
import com.openhis.whale.booking.domain.policy.entity.ReleasePolicy;
import com.openhis.whale.booking.domain.policy.entity.ScheduleChangePolicy;
import com.openhis.whale.booking.domain.policy.repository.RegistrationPolicyRepository;
import com.openhis.whale.booking.domain.policy.repository.ReleasePolicyRepository;
import com.openhis.whale.booking.domain.policy.repository.ScheduleChangePolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PolicyQueryService {

    private final ReleasePolicyRepository releasePolicyRepository;
    private final RegistrationPolicyRepository registrationPolicyRepository;
    private final ScheduleChangePolicyRepository scheduleChangePolicyRepository;

    public Page<ReleasePolicy> pageReleasePolicies(Long organizationId, Pageable pageable) {
        Specification<ReleasePolicy> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            predicates.getExpressions().add(cb.equal(root.get("isDeleted"), false));
            if (organizationId != null) {
                predicates.getExpressions().add(cb.equal(root.get("organizationId"), organizationId));
            }
            return predicates;
        };
        return releasePolicyRepository.findAll(spec, pageable);
    }

    public Optional<ReleasePolicy> findReleasePolicyByCode(Long organizationId, String policyCode) {
        return releasePolicyRepository.findByOrganizationIdAndPolicyCodeAndIsDeletedFalse(organizationId, policyCode);
    }

    public ReleasePolicy loadReleasePolicyById(Long id) {
        return releasePolicyRepository.findById(id).orElse(null);
    }

    public Optional<RegistrationPolicy> findRegistrationPolicyByOrganization(Long organizationId) {
        return registrationPolicyRepository.findFirstByOrganizationIdAndIsDeletedFalse(organizationId);
    }

    public RegistrationPolicy loadRegistrationPolicyById(Long id) {
        return registrationPolicyRepository.findById(id).orElse(null);
    }

    /**
     * 解析科室停诊处置策略；无配置时返回内存默认（07 §4.2）。
     *
     * @param organizationId 科室主键
     * @return 有效策略
     */
    public ScheduleChangePolicy resolveScheduleChangePolicy(Long organizationId) {
        if (organizationId == null) {
            return ScheduleChangePolicy.defaultForOrganization(null);
        }
        return scheduleChangePolicyRepository.findFirstByOrganizationIdAndIsDeletedFalse(organizationId)
            .orElse(ScheduleChangePolicy.defaultForOrganization(organizationId));
    }

    /**
     * 按科室加载已落库的停诊策略。
     */
    public Optional<ScheduleChangePolicy> findScheduleChangePolicyByOrganization(Long organizationId) {
        if (organizationId == null) {
            return Optional.empty();
        }
        return scheduleChangePolicyRepository.findFirstByOrganizationIdAndIsDeletedFalse(organizationId);
    }
}
