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
import com.openhis.whale.booking.domain.template.constants.BookingTemplateCodeConstants;
import com.openhis.whale.booking.domain.template.support.BookingTemplateCodeSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;


/**
 * 预约策略写侧（放号、挂号、停诊变更）。
 *
 * <p>边界：策略 CRUD 与科室唯一性收口；策略解析与 JSON 载荷由应用层编排。</p>
 */
@Service
@RequiredArgsConstructor
public class PolicyDomainService {

    private final ReleasePolicyRepository releasePolicyRepository;
    private final RegistrationPolicyRepository registrationPolicyRepository;
    private final ScheduleChangePolicyRepository scheduleChangePolicyRepository;

    /**
     * 保存放号策略。
     *
     * @param policy 策略实体
     * @param operatorId 操作人
     * @return 落库后的策略
     */
    public ReleasePolicy saveReleasePolicy(ReleasePolicy policy, Long operatorId) {
        // 前置：编码未传时自动生成，更新时保留原编码
        this.applyReleasePolicyCode(policy);
        LocalDateTime now = LocalDateTime.now();
        // 新建时补齐审计与软删标记
        if (policy.getId() == null) {
            policy.setCreatedAt(now);
            policy.setCreatedBy(operatorId);
            policy.setIsDeleted(Boolean.FALSE);
        }
        policy.setUpdatedAt(now);
        policy.setUpdatedBy(operatorId);
        // 持久化
        return releasePolicyRepository.save(policy);
    }

    /**
     * 软删放号策略。
     *
     * @param id 策略主键
     * @param operatorId 操作人
     */
    public void deleteReleasePolicy(Long id, Long operatorId) {
        releasePolicyRepository.findById(id).ifPresent(policy -> {
            policy.setIsDeleted(Boolean.TRUE);
            policy.setDeletedAt(LocalDateTime.now());
            policy.setDeletedBy(operatorId);
            releasePolicyRepository.save(policy);
        });
    }

    /**
     * 保存挂号策略。
     *
     * @param policy 策略实体
     * @param operatorId 操作人
     * @return 落库后的策略
     */
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

    /**
     * 软删挂号策略。
     *
     * @param id 策略主键
     * @param operatorId 操作人
     */
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
     *
     * @param policy 策略实体
     * @param operatorId 操作人
     * @return 落库后的策略
     */
    public ScheduleChangePolicy saveScheduleChangePolicy(ScheduleChangePolicy policy, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        // 同科室新建时复用已有记录主键，保证唯一有效策略
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

    /**
     * 解析放号策略待持久化编码：显式传入 / 更新沿用 / 新建自动生成。
     *
     * @param policy 待保存的放号策略（含 id 与 policyCode）
     */
    private void applyReleasePolicyCode(ReleasePolicy policy) {
        String existingCode = null;
        if (policy.getId() != null) {
            // 更新场景：读取库中编码以便未传时沿用
            existingCode = releasePolicyRepository.findById(policy.getId())
                .map(ReleasePolicy::getPolicyCode)
                .orElse(null);
        }
        policy.setPolicyCode(BookingTemplateCodeSupport.resolvePersistedCode(
            policy.getPolicyCode(),
            existingCode,
            BookingTemplateCodeConstants.PREFIX_RELEASE_POLICY));
    }
}
