/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.priceadjustment.service;

import com.zxplat.domain.custom.priceadjustment.command.ApprovePriceAdjustmentCommand;
import com.zxplat.domain.custom.priceadjustment.command.RejectPriceAdjustmentCommand;
import com.zxplat.domain.custom.priceadjustment.command.ResubmitPriceAdjustmentCommand;
import com.zxplat.domain.custom.priceadjustment.command.SavePriceAdjustmentDraftCommand;
import com.zxplat.domain.custom.priceadjustment.command.SubmitPriceAdjustmentCommand;
import com.zxplat.domain.custom.priceadjustment.command.WithdrawPriceAdjustmentDraftCommand;
import com.zxplat.domain.custom.priceadjustment.entity.PriceAdjustment;
import com.zxplat.domain.custom.priceadjustment.entity.PriceAdjustmentDetail;
import com.zxplat.domain.custom.priceadjustment.repository.PriceAdjustmentDetailRepository;
import com.zxplat.domain.custom.priceadjustment.repository.PriceAdjustmentRepository;
import com.zxplat.domain.custom.priceadjustment.support.PriceAdjustmentItemTableSupport;
import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.domain.share.constants.BusinessSequenceConstants;
import com.zxplat.domain.share.constants.CustomConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.PriceAdjustmentStatus;
import com.zxplat.domain.share.events.custom.priceadjustment.PriceAdjustmentApprovedEvent;
import com.zxplat.domain.share.events.custom.priceadjustment.PriceAdjustmentDraftSavedEvent;
import com.zxplat.domain.share.events.custom.priceadjustment.PriceAdjustmentRejectedEvent;
import com.zxplat.domain.share.events.custom.priceadjustment.PriceAdjustmentResubmittedEvent;
import com.zxplat.domain.share.events.custom.priceadjustment.PriceAdjustmentSnapshotEvent;
import com.zxplat.domain.share.events.custom.priceadjustment.PriceAdjustmentSubmittedEvent;
import com.zxplat.domain.share.events.custom.priceadjustment.PriceAdjustmentWithdrawnEvent;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 价格调整写侧领域服务（DM-CUS-011 v2 单据模型）。
 *
 * <p>聚合边界：仅 {@code cus_price_adjustment} 主行 + {@code cus_price_adjustment_detail} 子表。</p>
 * <p>不变量：草稿可编辑、审批态迁移、子表全量对齐；持久化先于调价领域事件（B.14）。</p>
 * <p>仅注入本聚合 {@link PriceAdjustmentRepository} 与 {@link PriceAdjustmentDetailRepository}；
 * 跨聚合读价/写价由应用层 {@link com.zxplat.domain.custom.priceadjustment.support.PriceAdjustmentItemTableSupport} 编排（B.6.4）。</p>
 */
@Service
@RequiredArgsConstructor
public class PriceAdjustmentDomainService {

    private final PriceAdjustmentRepository repository;
    private final PriceAdjustmentDetailRepository detailRepository;
    private final IDomainEventPublisher domainEventPublisher;
    private final BusinessSequenceService businessSequenceService;

    /**
     * 保存调价草稿：整单覆盖主行与子表（不加锁，BR-PRC-SNAP-002）。
     *
     * @param command 草稿命令；子表须由应用层预组装
     * @return 调价单号（新建或沿用）
     * @throws BusinessException 必填缺失、状态不可编辑或业务规则不满足时
     */
    public String savePriceAdjustmentDraft(SavePriceAdjustmentDraftCommand command) {
        // 前置校验：命令必填、同单 item_table 一致、项目表白名单（BR-PRC-SUB-001）
        this.assertSaveCommand(command);
        String busNo = this.resolveBusNo(command.getBusNo());
        List<SavePriceAdjustmentDraftCommand.Line> lines = command.getLines();
        this.assertLinesItemTableConsistent(lines);
        this.assertAllowedItemTables(lines);

        List<PriceAdjustment> existing = repository.findByBusNoAndIsDeletedFalse(busNo);
        // 已存在单须 draft/rejected，禁止覆盖 pending/approved
        this.assertEditableDocument(existing);

        Date adjustmentTime = command.getAdjustmentTime();
        if (adjustmentTime == null) {
            adjustmentTime = new Date();
        }

        // 全量对齐：软删旧主行与子表后重建（草稿可刷新快照）
        this.softDeleteDetailsOfLines(existing);
        this.softDeleteLines(existing);

        List<PriceAdjustment> savedLines = new ArrayList<>();
        for (SavePriceAdjustmentDraftCommand.Line line : lines) {
            this.assertLineBusinessRules(line);
            PriceAdjustment row = new PriceAdjustment();
            row.setBusNo(busNo);
            row.setItemTable(line.getItemTable().trim());
            row.setItemId(line.getItemId());
            if (PriceAdjustmentItemTableSupport.isClinicalItemTable(line.getItemTable())) {
                // 诊疗仅目标售价，禁止落目标进价（BR-PRC-CLIN-002）
                row.setNewPurchaseAmount(null);
                row.setNewSellingAmount(line.getNewSellingAmount());
            } else {
                row.setNewPurchaseAmount(line.getNewPurchaseAmount());
                row.setNewSellingAmount(line.getNewSellingAmount());
            }
            row.setAdjustmentReason(command.getAdjustmentReason().trim());
            row.setAdjustmentTime(adjustmentTime);
            row.setOperatorId(command.getOperatorId());
            row.setOrgId(command.getOrgId());
            row.initDraft();
            row.assertRequiredFields();
            savedLines.add(row);
        }
        // 持久化：主行 draft
        List<PriceAdjustment> persisted = repository.saveAll(savedLines);

        for (int index = 0; index < persisted.size(); index++) {
            PriceAdjustment row = persisted.get(index);
            SavePriceAdjustmentDraftCommand.Line line = lines.get(index);
            this.persistDetailsFromCommand(row, line);
        }

        // 发布事件：须在 save 之后、beforeCommit
        PriceAdjustment anchor = persisted.get(0);
        PriceAdjustmentDraftSavedEvent event = new PriceAdjustmentDraftSavedEvent(anchor.getId());
        this.buildSnapshotEvent(event, anchor);
        domainEventPublisher.publish(event);
        return busNo;
    }

    /**
     * 提交调价单审批：固化子表盈亏并迁移 pending（互斥、加锁由应用层前置编排）。
     *
     * @param command 提交命令
     * @throws BusinessException 本聚合 pending 互斥、子表未就绪或状态非法时
     */
    public void submitPriceAdjustmentForApproval(SubmitPriceAdjustmentCommand command) {
        String busNo = this.requireBusNo(command.getBusNo());
        List<PriceAdjustment> lines = this.requireDocumentLines(busNo);
        // 前置：仅 draft/rejected 可提交
        this.assertDocumentStatus(lines, PriceAdjustmentStatus.DRAFT, PriceAdjustmentStatus.REJECTED);
        // 本聚合 pending 互斥（BR-PRC-SUB-002）；在途与锁由应用层前置
        this.assertNoOtherPendingForItems(lines);

        // 提交固化：重算子表盈亏，pending 后不再变更（BR-PRC-SNAP-001）
        this.recalculateDetailImpacts(lines);
        this.assertDetailSnapshotReady(lines);

        Date submitTime = command.getSubmitTime();
        if (submitTime == null) {
            submitTime = new Date();
        }
        for (PriceAdjustment row : lines) {
            row.submit(command.getSubmitterId(), submitTime);
        }
        List<PriceAdjustment> saved = repository.saveAll(lines);

        PriceAdjustment anchor = saved.get(0);
        PriceAdjustmentSubmittedEvent event = new PriceAdjustmentSubmittedEvent(anchor.getId());
        this.buildSnapshotEvent(event, anchor);
        if (command.getLockedInventoryCount() != null) {
            event.setLockedInventoryCount(command.getLockedInventoryCount());
        }
        domainEventPublisher.publish(event);
    }

    /**
     * 审批通过：迁移 approved（定价写价、解锁库存由应用层编排）。
     *
     * @param command 审批命令
     * @throws BusinessException 非 pending 或记录不存在时
     */
    public void approvePriceAdjustment(ApprovePriceAdjustmentCommand command) {
        String busNo = this.requireBusNo(command.getBusNo());
        List<PriceAdjustment> lines = this.requireDocumentLines(busNo);
        this.assertDocumentStatus(lines, PriceAdjustmentStatus.PENDING);

        Date approveTime = command.getApproveTime();
        if (approveTime == null) {
            approveTime = new Date();
        }
        for (PriceAdjustment row : lines) {
            row.approve(command.getApproverId(), command.getApproveComment(), approveTime);
        }
        List<PriceAdjustment> saved = repository.saveAll(lines);

        // 发布事件：写价与解锁由应用层在同事务内先于/后于本调用编排
        PriceAdjustment anchor = saved.get(0);
        PriceAdjustmentApprovedEvent event = new PriceAdjustmentApprovedEvent(anchor.getId());
        this.buildSnapshotEvent(event, anchor);
        domainEventPublisher.publish(event);
    }

    /**
     * 审批驳回：迁移 rejected（解锁库存由应用层编排）。
     *
     * @param command 驳回命令；驳回原因必填
     * @throws BusinessException 非 pending、驳回原因为空或记录不存在时
     */
    public void rejectPriceAdjustment(RejectPriceAdjustmentCommand command) {
        String busNo = this.requireBusNo(command.getBusNo());
        List<PriceAdjustment> lines = this.requireDocumentLines(busNo);
        this.assertDocumentStatus(lines, PriceAdjustmentStatus.PENDING);

        Date approveTime = command.getApproveTime();
        if (approveTime == null) {
            approveTime = new Date();
        }
        for (PriceAdjustment row : lines) {
            row.reject(command.getApproverId(), command.getApproveComment(), approveTime);
        }
        List<PriceAdjustment> saved = repository.saveAll(lines);

        PriceAdjustment anchor = saved.get(0);
        PriceAdjustmentRejectedEvent event = new PriceAdjustmentRejectedEvent(anchor.getId());
        this.buildSnapshotEvent(event, anchor);
        domainEventPublisher.publish(event);
    }

    /**
     * 驳回后再次提交：语义同 {@link #submitPriceAdjustmentForApproval(SubmitPriceAdjustmentCommand)}。
     *
     * @param command 再次提交命令
     * @throws BusinessException 非 rejected、本聚合互斥失败或子表未就绪时
     */
    public void resubmitPriceAdjustmentForApproval(ResubmitPriceAdjustmentCommand command) {
        String busNo = this.requireBusNo(command.getBusNo());
        List<PriceAdjustment> lines = this.requireDocumentLines(busNo);
        this.assertDocumentStatus(lines, PriceAdjustmentStatus.REJECTED);
        this.assertNoOtherPendingForItems(lines);

        this.recalculateDetailImpacts(lines);
        this.assertDetailSnapshotReady(lines);

        Date submitTime = command.getSubmitTime();
        if (submitTime == null) {
            submitTime = new Date();
        }
        for (PriceAdjustment row : lines) {
            row.submit(command.getSubmitterId(), submitTime);
        }
        List<PriceAdjustment> saved = repository.saveAll(lines);

        PriceAdjustment anchor = saved.get(0);
        PriceAdjustmentResubmittedEvent event = new PriceAdjustmentResubmittedEvent(anchor.getId());
        this.buildSnapshotEvent(event, anchor);
        if (command.getLockedInventoryCount() != null) {
            event.setLockedInventoryCount(command.getLockedInventoryCount());
        }
        domainEventPublisher.publish(event);
    }

    /**
     * 撤销草稿：仅 draft → deprecated，不涉及解锁。
     *
     * @param command 撤销命令
     * @throws BusinessException 非 draft 或记录不存在时
     */
    public void withdrawPriceAdjustmentDraft(WithdrawPriceAdjustmentDraftCommand command) {
        String busNo = this.requireBusNo(command.getBusNo());
        List<PriceAdjustment> lines = this.requireDocumentLines(busNo);
        this.assertDocumentStatus(lines, PriceAdjustmentStatus.DRAFT);

        for (PriceAdjustment row : lines) {
            row.withdraw();
        }
        List<PriceAdjustment> saved = repository.saveAll(lines);

        PriceAdjustment anchor = saved.get(0);
        PriceAdjustmentWithdrawnEvent event = new PriceAdjustmentWithdrawnEvent(anchor.getId());
        this.buildSnapshotEvent(event, anchor);
        domainEventPublisher.publish(event);
    }

    /**
     * 校验保存命令必填项（BR-PRC-007、BR-PRC-008）。
     *
     * @param command 草稿命令
     */
    private void assertSaveCommand(SavePriceAdjustmentDraftCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "保存命令不能为空");
        }
        if (command.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "操作人不能为空");
        }
        if (command.getOrgId() == null) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "机构不能为空");
        }
        if (command.getAdjustmentReason() == null || command.getAdjustmentReason().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "调整原因不能为空");
        }
        if (command.getLines() == null || command.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "调价项目行不能为空");
        }
    }

    /**
     * 解析或按日序生成调价单号。
     *
     * @param busNo 入参单号；空则生成 PAD 日序
     * @return 有效单号
     */
    private String resolveBusNo(String busNo) {
        if (busNo == null || busNo.trim().isEmpty()) {
            return businessSequenceService.getSeqByDay(
                AggregateTypeConstants.PRICE_ADJUSTMENT,
                BusinessSequenceConstants.BUS_NO_SEQ_LENGTH
            );
        }
        return busNo.trim();
    }

    /**
     * 校验同单项目表一致（BR-PRC-SUB-001 / BR-PRC-010）。
     *
     * @param lines 项目行
     */
    private void assertLinesItemTableConsistent(List<SavePriceAdjustmentDraftCommand.Line> lines) {
        String expectedTable = null;
        for (SavePriceAdjustmentDraftCommand.Line line : lines) {
            if (line == null || line.getItemTable() == null || line.getItemTable().trim().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "项目表名不能为空");
            }
            if (line.getItemId() == null) {
                throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "项目标识不能为空");
            }
            String table = line.getItemTable().trim();
            if (expectedTable == null) {
                expectedTable = table;
            } else if (!expectedTable.equals(table)) {
                throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_ITEM_TABLE_MISMATCH, "同一调价单内项目表必须一致");
            }
        }
    }

    /**
     * 校验项目表属于允许调价的物理表。
     *
     * @param lines 项目行
     */
    private void assertAllowedItemTables(List<SavePriceAdjustmentDraftCommand.Line> lines) {
        for (SavePriceAdjustmentDraftCommand.Line line : lines) {
            String table = line.getItemTable().trim();
            if (!PriceAdjustmentItemTableSupport.isLogisticsItemTable(table)
                && !PriceAdjustmentItemTableSupport.isClinicalItemTable(table)) {
                throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "不支持的项目表调价");
            }
        }
    }

    /**
     * 按项目表分支校验行内目标价与子表入参。
     *
     * @param line 项目行
     */
    private void assertLineBusinessRules(SavePriceAdjustmentDraftCommand.Line line) {
        String table = line.getItemTable().trim();
        if (PriceAdjustmentItemTableSupport.isClinicalItemTable(table)) {
            if (line.getNewPurchaseAmount() != null) {
                throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "诊疗调价不支持目标进价");
            }
            if (line.getNewSellingAmount() == null) {
                throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "诊疗调价须填写目标售价");
            }
            if (line.getDetails() != null && line.getDetails().size() > 1) {
                throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "诊疗调价仅允许一条调前价明细");
            }
            BigDecimal oldSelling = this.resolveClinicalOldSellingAmount(line);
            if (oldSelling == null) {
                throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "诊疗调价须填写调前售价");
            }
            return;
        }
        if (PriceAdjustmentItemTableSupport.isLogisticsItemTable(table)) {
            // 物流至少一侧目标价，且须带批号明细（应用层组装）
            if (line.getNewPurchaseAmount() == null && line.getNewSellingAmount() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT,
                    "目标进价与目标售价至少填写一项"
                );
            }
            if (line.getDetails() == null || line.getDetails().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "药品耗材调价须填写批号明细");
            }
        }
    }

    /**
     * 校验单据可编辑（draft/rejected，BR-PRC-004）。
     *
     * @param lines 现有主行
     */
    private void assertEditableDocument(List<PriceAdjustment> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (PriceAdjustment row : lines) {
            row.assertEditable();
        }
    }

    /**
     * 软删主行（全量对齐前清理旧版本）。
     *
     * @param lines 主行列表
     */
    private void softDeleteLines(List<PriceAdjustment> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (PriceAdjustment row : lines) {
            row.setIsDeleted(true);
        }
        repository.saveAll(lines);
    }

    /**
     * 软删主行下全部未删明细。
     *
     * @param lines 主行列表
     */
    private void softDeleteDetailsOfLines(List<PriceAdjustment> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<Long> adjustmentIds = new ArrayList<>();
        for (PriceAdjustment row : lines) {
            adjustmentIds.add(row.getId());
        }
        List<PriceAdjustmentDetail> details = detailRepository.findByAdjustmentIdInAndIsDeletedFalse(adjustmentIds);
        if (details.isEmpty()) {
            return;
        }
        for (PriceAdjustmentDetail detail : details) {
            detail.setIsDeleted(true);
        }
        detailRepository.saveAll(details);
    }

    /**
     * 按命令写入子表并计算盈亏（仅本聚合持久化）。
     *
     * @param row  已持久化主行
     * @param line 命令行
     */
    private void persistDetailsFromCommand(PriceAdjustment row, SavePriceAdjustmentDraftCommand.Line line) {
        if (PriceAdjustmentItemTableSupport.isClinicalItemTable(row.getItemTable())) {
            this.persistClinicalDetailFromCommand(row, line);
            return;
        }
        List<SavePriceAdjustmentDraftCommand.DetailLine> detailLines = line.getDetails();
        if (detailLines == null || detailLines.isEmpty()) {
            return;
        }
        List<PriceAdjustmentDetail> toSave = new ArrayList<>();
        for (SavePriceAdjustmentDraftCommand.DetailLine detailLine : detailLines) {
            if (detailLine == null) {
                continue;
            }
            if (detailLine.getLotNumber() == null || detailLine.getLotNumber().trim().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "批号不能为空");
            }
            if (detailLine.getQuantity() == null || detailLine.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "批号明细数量须大于零");
            }
            PriceAdjustmentDetail detail = new PriceAdjustmentDetail();
            detail.setAdjustmentId(row.getId());
            // 库位可空：有则落库供日结按库过滤；写价/盈亏公式不读本字段
            detail.setLocationId(detailLine.getLocationId());
            detail.setLotNumber(detailLine.getLotNumber().trim());
            detail.setQuantity(detailLine.getQuantity());
            detail.setOldPurchaseAmount(detailLine.getOldPurchaseAmount());
            detail.setOldSellingAmount(detailLine.getOldSellingAmount());
            detail.recalculateImpact(row.getNewPurchaseAmount(), row.getNewSellingAmount());
            toSave.add(detail);
        }
        if (!toSave.isEmpty()) {
            detailRepository.saveAll(toSave);
        }
    }

    /**
     * 诊疗一对一子表：仅存调前售价，数量固定为 1。
     *
     * @param row  已持久化主行
     * @param line 命令行
     */
    private void persistClinicalDetailFromCommand(PriceAdjustment row, SavePriceAdjustmentDraftCommand.Line line) {
        BigDecimal oldSelling = this.resolveClinicalOldSellingAmount(line);
        PriceAdjustmentDetail detail = new PriceAdjustmentDetail();
        detail.setAdjustmentId(row.getId());
        detail.setLotNumber(CustomConstants.PriceAdjustment.CLINICAL_DETAIL_LOT_PLACEHOLDER);
        detail.setQuantity(BigDecimal.ONE);
        detail.setOldPurchaseAmount(null);
        detail.setOldSellingAmount(oldSelling);
        detail.recalculateImpact(null, row.getNewSellingAmount());
        detailRepository.save(detail);
    }

    /**
     * 解析诊疗调前售价：行字段优先，其次单行 details。
     *
     * @param line 命令行
     * @return 调前售价
     */
    private BigDecimal resolveClinicalOldSellingAmount(SavePriceAdjustmentDraftCommand.Line line) {
        if (line.getOldSellingAmount() != null) {
            return line.getOldSellingAmount();
        }
        if (line.getDetails() == null || line.getDetails().isEmpty()) {
            return null;
        }
        SavePriceAdjustmentDraftCommand.DetailLine detailLine = line.getDetails().get(0);
        if (detailLine == null) {
            return null;
        }
        return detailLine.getOldSellingAmount();
    }

    /**
     * 按主行目标价重算已落库子表盈亏（提交固化，BR-PRC-SNAP-001、BR-PRC-013）。
     *
     * @param lines 主行列表
     */
    private void recalculateDetailImpacts(List<PriceAdjustment> lines) {
        List<Long> adjustmentIds = new ArrayList<>();
        for (PriceAdjustment row : lines) {
            adjustmentIds.add(row.getId());
        }
        List<PriceAdjustmentDetail> details = detailRepository.findByAdjustmentIdInAndIsDeletedFalse(adjustmentIds);
        for (PriceAdjustmentDetail detail : details) {
            PriceAdjustment owner = this.findOwnerLine(lines, detail.getAdjustmentId());
            if (owner == null) {
                continue;
            }
            detail.recalculateImpact(owner.getNewPurchaseAmount(), owner.getNewSellingAmount());
        }
        if (!details.isEmpty()) {
            detailRepository.saveAll(details);
        }
    }

    /**
     * 在主行列表中查找子表所属项目行。
     *
     * @param lines         主行列表
     * @param adjustmentId  子表外键
     * @return 匹配主行；无匹配时 null
     */
    private PriceAdjustment findOwnerLine(List<PriceAdjustment> lines, Long adjustmentId) {
        for (PriceAdjustment row : lines) {
            if (row.getId().equals(adjustmentId)) {
                return row;
            }
        }
        return null;
    }

    /**
     * 校验提交前子表快照就绪（物流多批号 / 诊疗一对一，BR-PRC-011、BR-PRC-CLIN-003）。
     *
     * @param lines 主行列表
     */
    private void assertDetailSnapshotReady(List<PriceAdjustment> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<Long> adjustmentIds = new ArrayList<>(lines.size());
        for (PriceAdjustment row : lines) {
            adjustmentIds.add(row.getId());
        }
        List<PriceAdjustmentDetail> allDetails =
            detailRepository.findByAdjustmentIdInAndIsDeletedFalse(adjustmentIds);
        Map<Long, List<PriceAdjustmentDetail>> detailsByAdjustmentId = new HashMap<>();
        for (PriceAdjustmentDetail detail : allDetails) {
            if (detail == null || detail.getAdjustmentId() == null) {
                continue;
            }
            detailsByAdjustmentId.computeIfAbsent(detail.getAdjustmentId(), key -> new ArrayList<>()).add(detail);
        }
        for (PriceAdjustment row : lines) {
            List<PriceAdjustmentDetail> details =
                detailsByAdjustmentId.getOrDefault(row.getId(), new ArrayList<>());
            if (PriceAdjustmentItemTableSupport.isClinicalItemTable(row.getItemTable())) {
                if (details.size() != 1) {
                    throw new BusinessException(
                        ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT,
                        "诊疗调价须一对一调前价明细"
                    );
                }
                PriceAdjustmentDetail detail = details.get(0);
                if (detail.getOldSellingAmount() == null) {
                    throw new BusinessException(
                        ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT,
                        "诊疗调价调前售价不能为空"
                    );
                }
                continue;
            }
            if (details.isEmpty()) {
                throw new BusinessException(
                    ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT,
                    "该项目无可用库存批号明细，禁止提交调价"
                );
            }
            for (PriceAdjustmentDetail detail : details) {
                if (detail.getQuantity() == null || detail.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException(
                        ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT,
                        "批号明细数量须大于零"
                    );
                }
            }
        }
    }

    /**
     * 本聚合 pending 互斥（BR-PRC-SUB-002）；在途临床与锁冲突由应用层前置校验。
     *
     * @param lines 本单主行
     */
    private void assertNoOtherPendingForItems(List<PriceAdjustment> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Map<String, Set<Long>> itemIdsByTable = new HashMap<>();
        for (PriceAdjustment row : lines) {
            if (row == null || row.getItemId() == null || row.getItemTable() == null) {
                continue;
            }
            itemIdsByTable
                .computeIfAbsent(row.getItemTable().trim(), key -> new HashSet<>())
                .add(row.getItemId());
        }
        for (Map.Entry<String, Set<Long>> entry : itemIdsByTable.entrySet()) {
            List<Long> itemIds = new ArrayList<>(entry.getValue());
            if (itemIds.isEmpty()) {
                continue;
            }
            List<PriceAdjustment> pendingRows =
                repository.findByItemTableAndItemIdInAndStatusEnumAndIsDeletedFalse(
                    entry.getKey(),
                    itemIds,
                    PriceAdjustmentStatus.PENDING.getCode()
                );
            if (pendingRows != null && !pendingRows.isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_ITEM_PENDING, "该项目存在待审批调价单");
            }
        }
    }

    /**
     * 加载单据主行，不存在则抛错。
     *
     * @param busNo 调价单号
     * @return 未删主行列表
     */
    private List<PriceAdjustment> requireDocumentLines(String busNo) {
        List<PriceAdjustment> lines = repository.findByBusNoAndIsDeletedFalse(busNo);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_NOT_FOUND, "价格调整记录不存在");
        }
        return lines;
    }

    /**
     * 解析非空调价单号。
     *
     * @param busNo 单号
     * @return trim 后单号
     */
    private String requireBusNo(String busNo) {
        if (busNo == null || busNo.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "调价单号不能为空");
        }
        return busNo.trim();
    }

    /**
     * 断言单据全部主行处于允许的状态集合之一。
     *
     * @param lines   主行
     * @param allowed 允许状态（可传多个）
     */
    private void assertDocumentStatus(List<PriceAdjustment> lines, PriceAdjustmentStatus... allowed) {
        for (PriceAdjustment row : lines) {
            boolean matched = false;
            for (PriceAdjustmentStatus status : allowed) {
                if (status.getCode().equals(row.getStatusEnum())) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_STATUS, "调价单状态不允许当前操作");
            }
        }
    }

    /**
     * 将锚点主行字段拷贝到事件快照载体（v2 单据字段）。
     *
     * @param event    事件实例（已构造聚合 id）
     * @param snapshot 锚点主行
     */
    private void buildSnapshotEvent(PriceAdjustmentSnapshotEvent event, PriceAdjustment snapshot) {
        event.setBusNo(snapshot.getBusNo());
        event.setItemTable(snapshot.getItemTable());
        event.setItemId(snapshot.getItemId());
        event.setNewPurchaseAmount(snapshot.getNewPurchaseAmount());
        event.setNewSellingAmount(snapshot.getNewSellingAmount());
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
    }
}
