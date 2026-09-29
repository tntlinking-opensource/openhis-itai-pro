/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.homepageedit.service;

import com.openhis.health.domain.mrms.caserecord.command.HomepageSaveCommand;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordHomepageDomainService;
import com.openhis.health.domain.mrms.homepageedit.command.ApproveHomepageEditCommand;
import com.openhis.health.domain.mrms.homepageedit.command.CreateHomepageEditApplicationCommand;
import com.openhis.health.domain.mrms.homepageedit.entity.HomepageEditApplication;
import com.openhis.health.domain.mrms.homepageedit.repository.HomepageEditApplicationRepository;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.health.domain.share.enums.HomepageEditApplicationStatus;
import com.openhis.health.domain.share.events.mrms.homepageedit.HomepageEditApplicationCreatedEvent;
import com.openhis.health.domain.share.events.mrms.homepageedit.HomepageEditApprovedEvent;
import com.openhis.health.domain.share.events.mrms.homepageedit.HomepageEditRejectedEvent;
import com.openhis.health.domain.share.events.mrms.homepageedit.HomepageEditSubmittedEvent;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 病案首页编辑申请写侧（DM-MRMS-003）。
 *
 * <p>仅本聚合仓储；回写首页经同限界上下文 {@link CaseRecordHomepageDomainService}。</p>
 */
@Service
@RequiredArgsConstructor
public class HomepageEditApplicationDomainService {

    private static final List<String> ACTIVE_STATUSES = List.of(
            HomepageEditApplicationStatus.DRAFTING.getCode(),
            HomepageEditApplicationStatus.PENDING_REVIEW.getCode());

    private final HomepageEditApplicationRepository homepageEditApplicationRepository;
    private final CaseRecordHomepageDomainService caseRecordHomepageDomainService;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 按主键加载未删申请。
     *
     * @param id 申请主键
     * @return 申请
     */
    public HomepageEditApplication requireById(Long id) {
        return homepageEditApplicationRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("首页编辑申请", id));
    }

    /**
     * 创建起草中申请（调用方须已确认病案 archived）。
     *
     * @param command 创建命令
     * @return 新建申请
     */
    @Transactional(rollbackFor = Exception.class)
    public HomepageEditApplication create(CreateHomepageEditApplicationCommand command) {
        // Pre：同病案不得已有在途申请
        List<HomepageEditApplication> active = homepageEditApplicationRepository
                .findByTenantIdAndCaseRecordIdAndStatusEnumInAndIsDeletedFalse(
                        command.getTenantId(), command.getCaseRecordId(), ACTIVE_STATUSES);
        if (!active.isEmpty()) {
            throw new BusinessException(MrmsErrorCodes.HOMEPAGE_EDIT_ACTIVE_EXISTS, "已有在途编辑申请");
        }
        // Cmd：初始化 DRAFTING
        HomepageEditApplication entity = new HomepageEditApplication();
        entity.initDrafting(
                command.getTenantId(),
                command.getCaseRecordId(),
                command.getEncounterId(),
                command.getApplyReason(),
                command.getFromVersion(),
                command.getContentJson(),
                command.getApplicantId(),
                command.getOperatorId());
        HomepageEditApplication saved = homepageEditApplicationRepository.save(entity);
        domainEventPublisher.publish(new HomepageEditApplicationCreatedEvent(
                saved.getId(),
                saved.getCaseRecordId(),
                saved.getTenantId(),
                saved.getApplicantId()));
        return saved;
    }

    /**
     * 暂存工作稿。
     *
     * @param applicationId 申请主键
     * @param newContentJson 工作稿 JSON
     * @param operatorId 操作人
     * @return 更新后申请
     */
    @Transactional(rollbackFor = Exception.class)
    public HomepageEditApplication saveDraft(Long applicationId, String newContentJson, Long operatorId) {
        HomepageEditApplication entity = requireById(applicationId);
        entity.saveDraft(newContentJson, operatorId);
        return homepageEditApplicationRepository.save(entity);
    }

    /**
     * 提交审核。
     *
     * @param applicationId 申请主键
     * @param operatorId 操作人
     * @return 更新后申请
     */
    @Transactional(rollbackFor = Exception.class)
    public HomepageEditApplication submitForReview(Long applicationId, Long operatorId) {
        HomepageEditApplication entity = requireById(applicationId);
        entity.submitForReview(operatorId);
        HomepageEditApplication saved = homepageEditApplicationRepository.save(entity);
        domainEventPublisher.publish(new HomepageEditSubmittedEvent(
                saved.getId(),
                saved.getCaseRecordId(),
                saved.getFromVersion(),
                saved.getToVersion(),
                saved.getTenantId(),
                operatorId));
        return saved;
    }

    /**
     * 同意：回写首页升版后标记申请 approved。
     *
     * @param command 含工作稿解析出的 HomepageSaveCommand
     * @return 更新后申请
     */
    @Transactional(rollbackFor = Exception.class)
    public HomepageEditApplication approve(ApproveHomepageEditCommand command) {
        HomepageEditApplication entity = requireById(command.getApplicationId());
        if (!HomepageEditApplicationStatus.PENDING_REVIEW.getCode().equals(entity.getStatusEnum())) {
            throw new BusinessException(MrmsErrorCodes.HOMEPAGE_EDIT_INVALID_STATUS, "仅待审核可同意");
        }
        HomepageSaveCommand homepageSaveCommand = command.getHomepageSaveCommand();
        if (homepageSaveCommand == null) {
            throw new BusinessException(MrmsErrorCodes.HOMEPAGE_EDIT_INVALID_STATUS, "缺少首页回写内容");
        }
        // Cmd：覆盖锚点与操作人，避免客户端篡改
        homepageSaveCommand.setCaseRecordId(entity.getCaseRecordId());
        homepageSaveCommand.setOperatorId(command.getReviewerId());
        caseRecordHomepageDomainService.applyApprovedEditSnapshot(
                homepageSaveCommand, entity.getFromVersion(), entity.getToVersion());
        entity.markApproved(command.getReviewerId());
        HomepageEditApplication saved = homepageEditApplicationRepository.save(entity);
        domainEventPublisher.publish(new HomepageEditApprovedEvent(
                saved.getId(),
                saved.getCaseRecordId(),
                saved.getFromVersion(),
                saved.getToVersion(),
                saved.getTenantId(),
                command.getReviewerId()));
        return saved;
    }

    /**
     * 驳回（终态）。
     *
     * @param applicationId 申请主键
     * @param rejectReason 驳回原因
     * @param reviewerId 审核人
     * @return 更新后申请
     */
    @Transactional(rollbackFor = Exception.class)
    public HomepageEditApplication reject(Long applicationId, String rejectReason, Long reviewerId) {
        HomepageEditApplication entity = requireById(applicationId);
        entity.markRejected(rejectReason, reviewerId);
        HomepageEditApplication saved = homepageEditApplicationRepository.save(entity);
        domainEventPublisher.publish(new HomepageEditRejectedEvent(
                saved.getId(),
                saved.getCaseRecordId(),
                saved.getRejectReason(),
                saved.getTenantId(),
                reviewerId));
        return saved;
    }
}
