/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.borrowrequest.service;

import com.openhis.health.domain.mrms.borrowrequest.entity.BorrowRequest;
import com.openhis.health.domain.mrms.borrowrequest.repository.BorrowRequestRepository;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 借阅申请（{@code mrms_borrow_request}）聚合写侧领域服务。
 *
 * <p>状态迁移委托 {@link BorrowRequest} 聚合方法；仅注入本聚合 {@link BorrowRequestRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class BorrowRequestDomainService {

    private final BorrowRequestRepository borrowRequestRepository;

    /**
     * 按主键加载未删除借阅申请。
     *
     * @param id 借阅申请主键
     * @return 借阅申请
     */
    public BorrowRequest requireById(Long id) {
        return borrowRequestRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("借阅申请", id));
    }

    /**
     * 提交借阅/复印申请，初始状态 pending。
     *
     * @param tenantId 租户主键
     * @param caseRecordId 病案主档主键
     * @param requestTypeCode 申请类型 code（borrow/copy）
     * @param applicantPractitionerId 申请人 practitioner 主键
     * @param applicantName 申请人姓名快照
     * @param applicantDeptName 申请人科室快照
     * @param purpose 借阅目的
     * @param scopeDesc 借阅范围说明
     * @param copyCount 复印份数（复印类型时）
     * @param patientAuthorizationInfo 患者授权信息摘要
     * @param operatorId 操作人
     * @return 新建的借阅申请
     */
    @Transactional(rollbackFor = Exception.class)
    public BorrowRequest apply(
            Long tenantId,
            Long caseRecordId,
            String requestTypeCode,
            Long applicantPractitionerId,
            String applicantName,
            String applicantDeptName,
            String purpose,
            String scopeDesc,
            Integer copyCount,
            String patientAuthorizationInfo,
            Long operatorId) {
        // Cmd：聚合根初始化 pending
        BorrowRequest entity = new BorrowRequest();
        entity.initApply(
                tenantId,
                caseRecordId,
                requestTypeCode,
                applicantPractitionerId,
                applicantName,
                applicantDeptName,
                purpose,
                scopeDesc,
                copyCount,
                patientAuthorizationInfo,
                operatorId);
        // 持久化
        return borrowRequestRepository.save(entity);
    }

    /**
     * 审批通过：pending → approved。
     *
     * @param id 借阅申请主键
     * @param comment 审批意见
     * @param operatorId 审批人
     * @return 更新后的申请
     */
    @Transactional(rollbackFor = Exception.class)
    public BorrowRequest approve(Long id, String comment, Long operatorId) {
        // Pre：加载申请；Cmd：聚合状态迁移
        BorrowRequest entity = requireById(id);
        entity.approve(comment, operatorId);
        // 持久化
        return borrowRequestRepository.save(entity);
    }

    /**
     * 审批拒绝：pending → rejected。
     *
     * @param id 借阅申请主键
     * @param comment 拒绝原因
     * @param operatorId 审批人
     * @return 更新后的申请
     */
    @Transactional(rollbackFor = Exception.class)
    public BorrowRequest reject(Long id, String comment, Long operatorId) {
        // Pre：加载申请；Cmd：聚合状态迁移
        BorrowRequest entity = requireById(id);
        entity.reject(comment, operatorId);
        // 持久化
        return borrowRequestRepository.save(entity);
    }

    /**
     * 借出登记：approved → borrowed。
     *
     * @param id 借阅申请主键
     * @param dueReturnAt 应还时间
     * @param operatorId 经办人
     * @return 更新后的申请
     */
    @Transactional(rollbackFor = Exception.class)
    public BorrowRequest markBorrowed(Long id, LocalDateTime dueReturnAt, Long operatorId) {
        // Pre：加载申请；Cmd：登记借出时间与应还日
        BorrowRequest entity = requireById(id);
        entity.markBorrowed(dueReturnAt, operatorId);
        // 持久化
        return borrowRequestRepository.save(entity);
    }

    /**
     * 归还登记：borrowed → returned。
     *
     * @param id 借阅申请主键
     * @param operatorId 经办人
     * @return 更新后的申请
     */
    @Transactional(rollbackFor = Exception.class)
    public BorrowRequest markReturned(Long id, Long operatorId) {
        // Pre：加载申请；Cmd：登记归还
        BorrowRequest entity = requireById(id);
        entity.markReturned(operatorId);
        // 持久化
        return borrowRequestRepository.save(entity);
    }
}
