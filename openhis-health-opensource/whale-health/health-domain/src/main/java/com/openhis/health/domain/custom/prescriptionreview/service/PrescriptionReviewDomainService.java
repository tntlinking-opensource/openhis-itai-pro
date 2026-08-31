/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.prescriptionreview.service;

import com.openhis.health.domain.custom.prescriptionreview.command.CreatePrescriptionReviewCommand;
import com.openhis.health.domain.custom.prescriptionreview.command.CreatePrescriptionReviewItemCommand;
import com.openhis.health.domain.custom.prescriptionreview.command.CreatePrescriptionReviewsCommand;
import com.openhis.health.domain.custom.prescriptionreview.command.RevokePrescriptionReviewCommand;
import com.openhis.health.domain.custom.prescriptionreview.entity.PrescriptionReview;
import com.openhis.health.domain.custom.prescriptionreview.repository.PrescriptionReviewRepository;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.custom.prescriptionreview.PrescriptionReviewCreatedEvent;
import com.openhis.health.domain.share.events.custom.prescriptionreview.PrescriptionReviewRevokedEvent;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 处方点评写侧领域服务（DM-CUS-015）。
 *
 * <p>职责：创建/批量创建有效点评、撤销（软删）以支持重评；保证同一处方号仅一条有效行。</p>
 * <p>不变量：BR-PRV-001～005；持久化先于领域事件发布。</p>
 * <p>仅注入本聚合 {@link PrescriptionReviewRepository} 与日序号服务（B.10.9）。</p>
 */
@Service
@RequiredArgsConstructor
public class PrescriptionReviewDomainService {

    private final PrescriptionReviewRepository repository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 点评业务号日序（前缀 {@link AggregateTypeConstants#PRESCRIPTION_REVIEW}=PRV）。 */
    private final BusinessSequenceService businessSequenceService;

    /**
     * 创建单条处方点评。
     *
     * @param command 创建命令
     * @return 点评主键
     * @throws BusinessException 已有有效点评、原因缺失或参数非法时
     */
    public Long createReview(CreatePrescriptionReviewCommand command) {
        // 前置校验：命令缺失则无法落库
        if (command == null) {
            throw new BusinessException(ErrorCodeConstants.PRESCRIPTION_REVIEW_INVALID_ARGUMENT, "创建点评命令不能为空");
        }
        // 前置校验：有效点评一对一，已存在则拒绝新建（BR-PRV-001）
        if (StringUtils.isNotEmpty(command.getPrescriptionNo())
            && repository.existsByPrescriptionNoAndIsDeletedFalse(command.getPrescriptionNo().trim())) {
            throw new BusinessException(ErrorCodeConstants.PRESCRIPTION_REVIEW_ALREADY_EXISTS, "该处方已有有效点评");
        }

        // 组装聚合：业务号本聚合自生成（PRV 日序），不接受外域快照/前端传入
        PrescriptionReview entity = this.buildNewReview(
            this.nextBusNo(),
            command.getPrescriptionNo(),
            command.getReviewerId(),
            command.getReviewedAt(),
            command.getReasonableFlag(),
            command.getReason(),
            command.getRemark());
        entity.assertRequiredFields();
        entity.assertReasonWhenUnreasonable();
        entity.markReviewed();

        // 持久化：先 save 再发事件，保证 beforeCommit 可见行
        PrescriptionReview saved = repository.save(entity);

        // 发布事件：Created（beforeCommit）
        domainEventPublisher.publish(new PrescriptionReviewCreatedEvent(
            saved.getId(),
            saved.getPrescriptionNo(),
            saved.getBusNo(),
            saved.getReviewerId(),
            saved.getReasonableFlag(),
            saved.getStatusEnum(),
            command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 批量创建处方点评（共用是否合理/原因/备注）。
     *
     * @param command 批量命令
     * @return 新建点评主键列表
     * @throws BusinessException 任一条冲突或校验失败时整批失败
     */
    public List<Long> createReviews(CreatePrescriptionReviewsCommand command) {
        // 前置校验：批次头或 items 为空则整批拒绝
        if (command == null || command.getItems() == null || command.getItems().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PRESCRIPTION_REVIEW_INVALID_ARGUMENT, "批量点评列表不能为空");
        }
        List<Long> ids = new ArrayList<>();
        for (CreatePrescriptionReviewItemCommand item : command.getItems()) {
            // 前置校验：空子项无法映射为单条命令
            if (item == null) {
                throw new BusinessException(ErrorCodeConstants.PRESCRIPTION_REVIEW_INVALID_ARGUMENT, "批量点评子项不能为空");
            }
            // 组装聚合：头字段下沉到单条命令，复用 createReview（业务号在 createReview 内自生成）
            CreatePrescriptionReviewCommand single = new CreatePrescriptionReviewCommand();
            single.setPrescriptionNo(item.getPrescriptionNo());
            single.setReviewerId(command.getReviewerId());
            single.setReviewedAt(command.getReviewedAt());
            single.setReasonableFlag(command.getReasonableFlag());
            single.setReason(command.getReason());
            single.setRemark(command.getRemark());
            single.setOperatorId(command.getOperatorId());
            // 持久化+发布事件：任一条失败由 UoW 回滚整批
            ids.add(this.createReview(single));
        }
        return ids;
    }

    /**
     * 撤销有效点评（软删），处方回到可再评。
     *
     * @param command 撤销命令（id 优先，否则处方号）
     * @throws BusinessException 无有效点评时
     */
    public void revokeReview(RevokePrescriptionReviewCommand command) {
        // 前置校验：命令缺失则无法定位目标行
        if (command == null) {
            throw new BusinessException(ErrorCodeConstants.PRESCRIPTION_REVIEW_INVALID_ARGUMENT, "撤销点评命令不能为空");
        }
        // 加载聚合：id 优先，否则处方号；无有效行则业务失败（BR-PRV-003）
        PrescriptionReview entity = this.requireActiveReview(command.getId(), command.getPrescriptionNo());
        entity.assertCanRevoke();

        // 持久化：软删后展示态回到待点评，允许再插新行
        entity.markDeleted(command.getOperatorId());
        repository.save(entity);

        // 发布事件：Revoked（beforeCommit）
        domainEventPublisher.publish(new PrescriptionReviewRevokedEvent(
            entity.getId(),
            entity.getPrescriptionNo(),
            entity.getBusNo(),
            command.getOperatorId()));
    }

    /**
     * 按日生成点评业务号（前缀 PRV，B.10.9）。
     *
     * @return 非空业务号
     */
    private String nextBusNo() {
        return this.businessSequenceService.getSeqByDay(
            AggregateTypeConstants.PRESCRIPTION_REVIEW,
            BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
    }

    /**
     * 装配新点评实体（未落库）。
     *
     * @param busNo           业务号（已由本服务生成）
     * @param prescriptionNo  处方号
     * @param reviewerId      点评人
     * @param reviewedAt      点评时间
     * @param reasonableFlag  是否合理
     * @param reason          原因
     * @param remark          备注
     * @return 新实体
     */
    private PrescriptionReview buildNewReview(
        String busNo,
        String prescriptionNo,
        Long reviewerId,
        LocalDateTime reviewedAt,
        Boolean reasonableFlag,
        String reason,
        String remark) {
        PrescriptionReview entity = new PrescriptionReview();
        // 业务号非空才写入，避免把 null 写成空串绕过必填
        if (busNo != null) {
            entity.setBusNo(busNo.trim());
        }
        // 处方号非空才写入，空串由后续 assertRequiredFields 拒绝
        if (prescriptionNo != null) {
            entity.setPrescriptionNo(prescriptionNo.trim());
        }
        entity.setReviewerId(reviewerId);
        // 点评时间为空则取领域当前时刻，保证 REVIEWED 行必有时间轴
        if (reviewedAt == null) {
            entity.setReviewedAt(LocalDateTime.now());
        } else {
            entity.setReviewedAt(reviewedAt);
        }
        entity.setReasonableFlag(reasonableFlag);
        // 原因可空（合理时）；有值则 trim 后落库
        if (reason != null) {
            entity.setReason(reason.trim());
        }
        // 备注可空；有值则 trim 后落库
        if (remark != null) {
            entity.setRemark(remark.trim());
        }
        return entity;
    }

    /**
     * 按主键或处方号定位有效点评。
     *
     * @param id             点评主键
     * @param prescriptionNo 处方号
     * @return 有效实体
     * @throws BusinessException 锚点皆空或不存在时
     */
    private PrescriptionReview requireActiveReview(Long id, String prescriptionNo) {
        // 主键优先：直接按有效行加载，找不到即视为已撤销或不存在
        if (id != null) {
            return repository.findByIdAndIsDeletedFalse(id).orElseThrow(
                () -> new BusinessException(ErrorCodeConstants.PRESCRIPTION_REVIEW_NOT_FOUND, "有效点评不存在"));
        }
        // 无主键时处方号必填，否则无法定位撤销目标
        if (StringUtils.isBlank(prescriptionNo)) {
            throw new BusinessException(ErrorCodeConstants.PRESCRIPTION_REVIEW_INVALID_ARGUMENT, "点评标识与处方号不能同时为空");
        }
        return repository.findByPrescriptionNoAndIsDeletedFalse(prescriptionNo.trim()).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.PRESCRIPTION_REVIEW_NOT_FOUND, "有效点评不存在"));
    }
}
