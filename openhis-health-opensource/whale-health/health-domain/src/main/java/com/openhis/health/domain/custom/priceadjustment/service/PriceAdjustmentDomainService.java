/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.custom.priceadjustment.service;

import com.openhis.health.domain.custom.priceadjustment.command.*;
import com.openhis.health.domain.custom.priceadjustment.entity.PriceAdjustment;
import com.openhis.health.domain.custom.priceadjustment.repository.PriceAdjustmentRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.custom.priceadjustment.*;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 价格调整写侧领域服务。
 *
 * <p>维护调价草稿、提交/审批/驳回/再次提交/撤回及批量落草稿；状态迁移由聚合根收口，事件携带调价快照字段。</p>
 */
@Service
@RequiredArgsConstructor
public class PriceAdjustmentDomainService {

    private final PriceAdjustmentRepository repository;
    private final IDomainEventPublisher domainEventPublisher;


    /**
     * 新建调价草稿：计算新旧差额（若金额齐全）、缺省调整时间为当前时刻，校验必填后落库并发布带快照的创建事件。
     *
     * @param command 定义、价格类型、批号、新旧金额及操作人等
     * @return 新记录主键
     * @throws BusinessException 必填或业务规则不满足时
     */
    public Long createPriceAdjustmentDraft(CreatePriceAdjustmentDraftCommand command) {
        PriceAdjustment adjustment = new PriceAdjustment();
        adjustment.setDefinitionId(command.getDefinitionId());
        adjustment.setPriceType(command.getPriceType());
        adjustment.setLotNumber(command.getLotNumber());
        adjustment.setOldAmount(command.getOldAmount());
        adjustment.setNewAmount(command.getNewAmount());
        adjustment.setAdjustmentReason(command.getAdjustmentReason());
        // 业务分支处理。
        if (command.getAdjustmentTime() == null) {
            adjustment.setAdjustmentTime(new Date());
        } else {
            adjustment.setAdjustmentTime(command.getAdjustmentTime());
        }
        adjustment.setOperatorId(command.getOperatorId());
        adjustment.setOrgId(command.getOrgId());
        adjustment.setStockQuantity(command.getStockQuantity());
        // 业务分支处理。
        if (command.getNewAmount() == null || command.getOldAmount() == null) {
            adjustment.setAdjustmentDelta(null);
        } else {
            adjustment.setAdjustmentDelta(command.getNewAmount().subtract(command.getOldAmount()));
        }
        adjustment.initDraft();

        adjustment.assertRequiredFields();

        PriceAdjustment saved = repository.save(adjustment);
        domainEventPublisher.publish(buildSnapshotEvent(new PriceAdjustmentCreatedEvent(saved.getId()), saved));
        return saved.getId();
    }

    /**
     * 将草稿提交审批：须尚未终审同意，记录提交人与时间后发布提交事件（含快照）。
     *
     * @param command 调价单主键、提交人与提交时间
     * @throws BusinessException 记录不存在或状态不允许提交时
     */
    public void submitForApproval(SubmitForApprovalCommand command) {
        PriceAdjustment adjustment = requireAdjustment(command.getAdjustmentId());
        adjustment.assertNotApproved();
        adjustment.submit(command.getSubmitterId(), command.getSubmitTime());
        PriceAdjustment saved = repository.save(adjustment);
        domainEventPublisher.publish(buildSnapshotEvent(new PriceAdjustmentSubmittedEvent(saved.getId()), saved));
    }

    /**
     * 审批同意调价：须尚未同意，写入审批人、意见与时间后发布同意事件（含快照）。
     *
     * @param command 调价单主键、审批人、意见与时间
     * @throws BusinessException 记录不存在或状态不允许同意时
     */
    public void approvePriceAdjustment(ApprovePriceAdjustmentCommand command) {
        PriceAdjustment adjustment = requireAdjustment(command.getAdjustmentId());
        adjustment.assertNotApproved();
        adjustment.approve(command.getApproverId(), command.getApproveComment(), command.getApproveTime());
        PriceAdjustment saved = repository.save(adjustment);
        domainEventPublisher.publish(buildSnapshotEvent(new PriceAdjustmentApprovedEvent(saved.getId()), saved));
    }

    /**
     * 审批驳回调价：须尚未同意，写入审批人、意见与时间后发布驳回事件（含快照）。
     *
     * @param command 调价单主键、审批人、意见与时间
     * @throws BusinessException 记录不存在或状态不允许驳回时
     */
    public void rejectPriceAdjustment(RejectPriceAdjustmentCommand command) {
        PriceAdjustment adjustment = requireAdjustment(command.getAdjustmentId());
        adjustment.assertNotApproved();
        adjustment.reject(command.getApproverId(), command.getApproveComment(), command.getApproveTime());
        PriceAdjustment saved = repository.save(adjustment);
        domainEventPublisher.publish(buildSnapshotEvent(new PriceAdjustmentRejectedEvent(saved.getId()), saved));
    }

    /**
     * 再次提交审批（与首次提交同一状态迁移语义），发布再次提交事件。
     *
     * @param command 调价单主键、提交人与提交时间
     * @throws BusinessException 记录不存在或状态不允许时
     */
    public void resubmitForApproval(ResubmitForApprovalCommand command) {
        PriceAdjustment adjustment = requireAdjustment(command.getAdjustmentId());
        adjustment.assertNotApproved();
        adjustment.submit(command.getSubmitterId(), command.getSubmitTime());
        PriceAdjustment saved = repository.save(adjustment);
        domainEventPublisher.publish(buildSnapshotEvent(new PriceAdjustmentResubmittedEvent(saved.getId()), saved));
    }

    /**
     * 撤回草稿/审批前状态，发布撤回事件（含快照）。
     *
     * @param command 调价单主键
     * @throws BusinessException 记录不存在或不允许撤回时
     */
    public void withdrawDraft(WithdrawDraftCommand command) {
        PriceAdjustment adjustment = requireAdjustment(command.getAdjustmentId());
        adjustment.withdraw();
        PriceAdjustment saved = repository.save(adjustment);
        domainEventPublisher.publish(buildSnapshotEvent(new PriceAdjustmentWithdrawnEvent(saved.getId()), saved));
    }

    /**
     * 批量生成调价草稿行：逐条初始化草稿、计算差额、校验必填后批量保存；至少一行时发布批量记录事件。
     *
     * @param command 明细列表与操作人
     * @return 新插入记录主键列表
     * @throws BusinessException 任一行校验失败时（随聚合断言抛出）
     */
    public List<Long> batchRecordPriceAdjustments(BatchRecordPriceAdjustmentsCommand command) {
        List<BatchRecordPriceAdjustmentsCommand.BatchRecordPriceAdjustmentItemCommand> items = command.resolveItems();
        List<PriceAdjustment> toSave = new ArrayList<>();
        for (BatchRecordPriceAdjustmentsCommand.BatchRecordPriceAdjustmentItemCommand item : items) {
            PriceAdjustment adjustment = new PriceAdjustment();
            adjustment.setDefinitionId(item.getDefinitionId());
            adjustment.setPriceType(item.getPriceType());
            adjustment.setLotNumber(item.getLotNumber());
            adjustment.setOldAmount(item.getOldAmount());
            adjustment.setNewAmount(item.getNewAmount());
            adjustment.setAdjustmentReason(item.getAdjustmentReason());
            // 业务分支处理。
            if (item.getAdjustmentTime() == null) {
                adjustment.setAdjustmentTime(new Date());
            } else {
                adjustment.setAdjustmentTime(item.getAdjustmentTime());
            }
            adjustment.setOperatorId(command.getOperatorId());
            adjustment.setOrgId(item.getOrgId());
            adjustment.setStockQuantity(item.getStockQuantity());
            // 业务分支处理。
            if (item.getOldAmount() != null && item.getNewAmount() != null) {
                adjustment.setAdjustmentDelta(item.getNewAmount().subtract(item.getOldAmount()));
            }
            adjustment.initDraft();
            adjustment.assertRequiredFields();
            toSave.add(adjustment);
        }
        List<PriceAdjustment> saved = repository.saveAll(toSave);
        List<Long> ids = new ArrayList<>();
        for (PriceAdjustment row : saved) {
            ids.add(row.getId());
        }
        // 业务分支处理。
        if (!ids.isEmpty()) {
            domainEventPublisher.publish(new PriceAdjustmentBatchRecordedEvent(ids.get(0), ids));
        }
        return ids;
    }

    /**
     * 按主键加载调价记录，不存在则抛出业务异常。
     *
     * @param adjustmentId 调价记录主键
     * @return 调价记录实体
     * @throws BusinessException 记录不存在时
     */
    private PriceAdjustment requireAdjustment(Long adjustmentId) {
        return repository.findById(adjustmentId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_NOT_FOUND, "价格调整记录不存在"));
    }

    /**
     * 将聚合当前字段拷贝到快照事件载体，供投影与集成消费。
     *
     * @param event 事件基类
     * @param snapshot 调价记录快照
     * @return 填充后的事件对象
     */
    private PriceAdjustmentSnapshotEvent buildSnapshotEvent(PriceAdjustmentSnapshotEvent event, PriceAdjustment snapshot) {
        // Asm：将聚合字段拷贝到事件载体。
        event.setDefinitionId(snapshot.getDefinitionId());
        event.setPriceType(snapshot.getPriceType());
        event.setLotNumber(snapshot.getLotNumber());
        event.setOldAmount(snapshot.getOldAmount());
        event.setNewAmount(snapshot.getNewAmount());
        event.setAdjustmentReason(snapshot.getAdjustmentReason());
        event.setAdjustmentTime(snapshot.getAdjustmentTime());
        event.setStatusEnum(snapshot.getStatusEnum());
        event.setSubmitterId(snapshot.getSubmitterId());
        event.setSubmitTime(snapshot.getSubmitTime());
        event.setApproverId(snapshot.getApproverId());
        event.setApproveTime(snapshot.getApproveTime());
        event.setApproveComment(snapshot.getApproveComment());
        event.setOperatorId(snapshot.getOperatorId());
        event.setOrgId(snapshot.getOrgId());
        event.setStockQuantity(snapshot.getStockQuantity());
        event.setAdjustmentDelta(snapshot.getAdjustmentDelta());
        event.setTotalImpact(snapshot.getTotalImpact());
        return event;
    }
}
