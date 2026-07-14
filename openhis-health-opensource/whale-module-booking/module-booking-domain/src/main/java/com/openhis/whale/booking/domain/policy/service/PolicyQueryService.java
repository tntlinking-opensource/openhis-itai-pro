/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
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

/**
 * 预约策略读侧查询。
 *
 * <p>只读；默认过滤 {@code isDeleted=false}。</p>
 */
@Service
@RequiredArgsConstructor
public class PolicyQueryService {

    private final ReleasePolicyRepository releasePolicyRepository;
    private final RegistrationPolicyRepository registrationPolicyRepository;
    private final ScheduleChangePolicyRepository scheduleChangePolicyRepository;

    /**
     * 分页查询放号策略。
     *
     * @param organizationId 机构主键（可选，为空则查全院有效策略）
     * @param pageable 分页
     * @return 策略分页
     */
    public Page<ReleasePolicy> pageReleasePolicies(Long organizationId, Pageable pageable) {
        Specification<ReleasePolicy> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            predicates.getExpressions().add(cb.equal(root.get("isDeleted"), false));
            // 按科室收窄时仅返回该科室策略
            if (organizationId != null) {
                predicates.getExpressions().add(cb.equal(root.get("organizationId"), organizationId));
            }
            return predicates;
        };
        return releasePolicyRepository.findAll(spec, pageable);
    }

    /**
     * 按机构与策略编码查有效放号策略。
     *
     * @param organizationId 机构主键
     * @param policyCode 策略编码
     * @return 策略；无则 empty
     */
    public Optional<ReleasePolicy> findReleasePolicyByCode(Long organizationId, String policyCode) {
        return releasePolicyRepository.findByOrganizationIdAndPolicyCodeAndIsDeletedFalse(organizationId, policyCode);
    }

    /**
     * 按主键加载放号策略（含已删，由调用方判断）。
     *
     * @param id 策略主键
     * @return 实体或 null
     */
    public ReleasePolicy loadReleasePolicyById(Long id) {
        return releasePolicyRepository.findById(id).orElse(null);
    }

    /**
     * 按机构查有效挂号策略（每机构至多一条）。
     *
     * @param organizationId 机构主键
     * @return 策略；无则 empty
     */
    public Optional<RegistrationPolicy> findRegistrationPolicyByOrganization(Long organizationId) {
        return registrationPolicyRepository.findFirstByOrganizationIdAndIsDeletedFalse(organizationId);
    }

    /**
     * 按主键加载挂号策略。
     *
     * @param id 策略主键
     * @return 实体或 null
     */
    public RegistrationPolicy loadRegistrationPolicyById(Long id) {
        return registrationPolicyRepository.findById(id).orElse(null);
    }

    /**
     * 解析科室停诊处置策略；无配置时返回内存默认（07 §4.2）。
     *
     * @param organizationId 科室主键
     * @return 有效策略（含默认）
     */
    public ScheduleChangePolicy resolveScheduleChangePolicy(Long organizationId) {
        // 无科室上下文时返回全局默认策略
        if (organizationId == null) {
            return ScheduleChangePolicy.defaultForOrganization(null);
        }
        return scheduleChangePolicyRepository.findFirstByOrganizationIdAndIsDeletedFalse(organizationId)
            .orElse(ScheduleChangePolicy.defaultForOrganization(organizationId));
    }

    /**
     * 按科室加载已落库的停诊策略。
     *
     * @param organizationId 科室主键
     * @return 已配置策略；未配置则 empty
     */
    public Optional<ScheduleChangePolicy> findScheduleChangePolicyByOrganization(Long organizationId) {
        if (organizationId == null) {
            return Optional.empty();
        }
        return scheduleChangePolicyRepository.findFirstByOrganizationIdAndIsDeletedFalse(organizationId);
    }
}
