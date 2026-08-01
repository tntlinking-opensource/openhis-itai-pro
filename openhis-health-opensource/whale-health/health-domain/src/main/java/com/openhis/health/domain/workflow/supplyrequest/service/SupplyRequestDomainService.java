/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplyrequest.service;

import com.openhis.health.domain.share.authorization.FinanceApprovalAuthorizer;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.SupplyCategory;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.events.workflow.supplyrequest.SummarySupplyRequestCancelledEvent;
import com.openhis.health.domain.share.events.workflow.supplyrequest.SupplyRequestAgreedEvent;
import com.openhis.health.domain.share.events.workflow.supplyrequest.SupplyRequestCreatedEvent;
import com.openhis.health.domain.share.events.workflow.supplyrequest.SupplyRequestDeletedEvent;
import com.openhis.health.domain.share.events.workflow.supplyrequest.SupplyRequestRejectedEvent;
import com.openhis.health.domain.share.events.workflow.supplyrequest.SupplyRequestSubmittedEvent;
import com.openhis.health.domain.share.events.workflow.supplyrequest.SupplyRequestWithdrawnEvent;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CancelSummarySupplyRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateDeptIssueCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateDeptIssueReturnCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateDeptPurchaseRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateDeviceSummarySupplyRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateGainLossCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateInboundReceiptCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateOpeningCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreatePurchaseOrderCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateRequisitionCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateReturnCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateReturnToSupplierCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateStockOutCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateStocktakeCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateSummarySupplyRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateTransferCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.VoidOpeningCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.entity.SupplyRequest;
import com.openhis.health.domain.workflow.supplyrequest.repository.SupplyRequestRepository;
import com.openhis.health.domain.workflow.supplyrequest.support.SupplyRequestWritePricingSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 供应请求写侧领域服务。
 *
 * <p>边界：本服务只负责本聚合落库与领域事件发布；SupplyDelivery/InventoryItem/MedicationDispense/DeviceDispense 的编排均由应用层完成。</p>
 *
 * <p>命令与实体中凡 {@code *_enum} 语义字段均与库表 VARCHAR 对齐，使用各业务枚举 {@code getCode()} 持久化字符串（B.10.8.1）；</p>
 * <p>不变量：状态迁移门禁、行级金额刻度（BR-MON-001）；持久化先于供应请求领域事件（B.14）。</p>
 * <p>仅注入本聚合 {@link SupplyRequestRepository}；跨聚合编排由应用层完成。</p>
 */
@Service
@RequiredArgsConstructor
public class SupplyRequestDomainService {

    private final SupplyRequestRepository supplyRequestRepository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 供应请求 busNo：外部未传时按日流水分配，序号键由 {@link SupplyType#busNoSeqPrefixForTypeCode(String)} 按 {@code type_enum} 分桶。 */
    private final BusinessSequenceService businessSequenceService;
    /** 写路径行级金额门禁（BR-MON-001）；持久化前强制重算或刻度协商单价。 */
    private final SupplyRequestWritePricingSupport writePricingSupport;
    /** 财务审批族供应单 agree/reject 须持有财务审批岗位（实现由 platform/authorization 装配）。 */
    private final FinanceApprovalAuthorizer financeApprovalAuthorizer;


    /**
     * 创建或修订药库订货单（同一聚合入口：{@link SupplyType#CABINET_PURCHASE_ORDER} 多行明细共享同一 {@code bus_no}）。
     *
     * <p><b>新建路径</b>：命令首行 {@link CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand#getBusNo()} 为空或空白时，
     * 由 {@link #generateSupplyBusNo(String)} 按业务日生成新 {@code busNo}（序号键见 {@link SupplyType#busNoSeqPrefixForTypeCode(String)}，
     * 序号策略见 {@link BusinessSequenceService}），再批量插入多行，状态统一为待审核；插入成功后发布
     * {@link SupplyRequestCreatedEvent}（须在持久化完成之后，见全局规范「先 save 后 publish」）。</p>
     *
     * {@code busNo} 时表示「同一张订货单修改明细」而非新开单。语义为<strong>全量明细对齐</strong>：先将该 {@code busNo}
     * 下当前未删除的订货行全部标记逻辑删除并一次性落库，再按命令中的明细列表<strong>整批重插</strong>为新行（仍为同一 {@code busNo}）。
     * 门控由 {@link SupplyRequest#assertPurchaseOrderLinesRevisable(List)} 承担：须为药库订货类型且各行均为可修订态
     * （待审核/驳回/已撤回，见 {@link SupplyRequest#isRevisableSupplyStatusCode(String)}），
     * 与 BR-SR-003 / 应用层 {@code verifyAbleEdit} 前置一致；若 {@code busNo} 在库中不存在则断言抛出「无法修订」。</p>
     *
     * <p><b>修订路径为何不发布 {@link SupplyRequestCreatedEvent}</b>（DOC-CONFLICT）：DM §5 当前清单以 Created 为主，
     * 未见「订货明细已替换」专用事件；若此处再次发布 Created，下游投影/集成可能将「编辑保存」误判为「又来一张新单」。
     * 若产品后续定义修订事件，应在此处补充 publish，并同步修订 DM 与消费方。</p>
     *
     * <p><b>与进货单边界</b>：本方法<strong>不</strong>创建进货单（{@link #createInboundReceipt(CreateInboundReceiptCommand)}），
     * 订货同意后的进货编排见其它应用服务（AS WHS-SDD-R-001）。</p>
     *
     * <p><b>可选上游科室采购申请</b>：命令行 {@code originalBusNo} 可空（手工直建）；非空须为已同意
     * {@link SupplyType#DEPT_PURCHASE_REQUEST}，且未被其它未删除订货占用（BR-WHS-PO-010/012）；修订路径自存量保留，禁止切换。</p>
     *
     * @param command 订货命令；须已通过 {@link CreatePurchaseOrderCommand#resolveItems()} 可用的批量语义，
     *                内含 {@code operatorId} 用于新建路径事件载荷；每行 item 须携带位置、品类、数量等业务字段
     * @return 最终生效的业务单号 {@code busNo}（新建为新分配号码，修订为入参既有号码）
     * @throws BusinessException 命令解析失败、修订时单号不存在、类型或状态不允许修订、乐观锁版本不匹配（{@link ErrorCodeConstants#WHS_PO_105}）、
     *                            来源申请不可引用（{@link ErrorCodeConstants#WHS_PO_106}）、或子实体校验失败
     */
    public String createPurchaseOrder(CreatePurchaseOrderCommand command) {
        // 前置：协商单价刻度与行总额（订货例外，BR-MON-001）
        this.writePricingSupport.applyPurchaseOrderCommand(command);
        // 双形态命令统一解析为列表；领域层要求至少一行，空列表由 resolveItems 或后续断言阻断。
        List<CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand> items = command.resolveItems();
        CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand head = items.get(0);
        String requestedBusNo = head.getBusNo();
        // 与 AS 约定一致：是否修订只看「首行是否显式携带业务单号」，新建场景调用方不应填 busNo。
        boolean revisionRequested = requestedBusNo != null && !requestedBusNo.trim().isEmpty();

        if (revisionRequested) {
            String busNo = requestedBusNo.trim();
            // 修订：按业务单号加载当前可见（未软删）的全部订货行，一次性读取便于批量标记删除。
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNoAndIsDeletedFalse(busNo);
            // 聚合不变式：须存在、须为药库订货类型、须仍可编辑（待审核/驳回/已撤回）；不满足则业务异常，禁止 silent skip。
            SupplyRequest.assertPurchaseOrderLinesRevisable(existingLines);

            Integer expectedVer = command.getExpectedRowVersionForRevision();
            if (expectedVer == null) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "修订订货单必须携带乐观锁版本 expectedRowVersionForRevision");
            }
            // 乐观锁：同号各行 version 须与前端 rowVersion 一致，防止并发覆盖（对齐进货修订，对外码 WHS_PO_105）
            for (SupplyRequest existingLine : existingLines) {
                if (existingLine == null) {
                    continue;
                }
                Integer ver = existingLine.getVersion();
                if (ver == null || !expectedVer.equals(ver)) {
                    throw new BusinessException(ErrorCodeConstants.WHS_PO_105, "订货单已被修改或版本不匹配，请刷新后重试");
                }
            }

            // 修订禁止切换来源申请：以存量首行 original_bus_no 为准写回命令行
            String preservedOriginalBusNo = existingLines.get(0).getOriginalBusNo();
            for (CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand item : items) {
                if (item == null) {
                    continue;
                }
                item.setOriginalBusNo(preservedOriginalBusNo);
            }

            // 先删后增：逻辑删除旧行并 flush，再由后续 saveAll 重插同号明细
            this.softDeleteExistingLinesForRevision(existingLines);

            List<SupplyRequest> toInsert = new ArrayList<>();
            for (CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand item : items) {
                SupplyRequest entity = this.buildPurchaseOrderEntityFromItem(busNo, item);
                entity.assertCreatableMinimumFields();
                toInsert.add(entity);
            }
            // 按命令「全量替换」后的明细批量插入；仍为同一 busNo，审批状态重新落在待审核（见 build 路径）。
            supplyRequestRepository.saveAll(toInsert);
            // 修订成功返回原单号；故意不调用 publishCreated，理由见方法 JavaDoc DOC-CONFLICT 说明。
            return busNo;
        }

        // ---------- 新建路径 ----------
        // 可选拉源：校验科室采购申请 AGREE 且未被其它未删除订货占用
        String linkOriginalBusNo = head.getOriginalBusNo();
        this.assertOptionalDeptPurchaseRequestLinkForPurchaseOrder(linkOriginalBusNo, null);
        // 同一张订货单各行共享同一 original_bus_no（可空）
        for (CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand item : items) {
            if (item == null) {
                continue;
            }
            item.setOriginalBusNo(linkOriginalBusNo);
        }

        // 首行未带来 busNo：生成当日流水号，保证与「业务发生日」归档口径一致（见通用规范 busNo 按日策略）。
        String typeForBusNo = head.getTypeEnum();
        if (typeForBusNo == null || typeForBusNo.isBlank()) {
            typeForBusNo = SupplyType.CABINET_PURCHASE_ORDER.getCode();
        }
        String busNo = this.generateSupplyBusNo(typeForBusNo);

        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand item : items) {
            SupplyRequest entity = this.buildPurchaseOrderEntityFromItem(busNo, item);
            entity.assertCreatableMinimumFields();
            toSave.add(entity);
        }

        List<SupplyRequest> saved = supplyRequestRepository.saveAll(toSave);
        // 新建：仅在整批明细落库成功后发布创建事件，避免下游收到通知却查无数据（幽灵事件）。
        this.publishCreated(saved.get(0), command.getOperatorId(), items.size());
        return busNo;
    }

    /**
     * 由订货命令项装配一行聚合实体（新建与修订重插共用）。
     *
     * @param busNo 业务单号（修订与新建均已确定）
     * @param item  命令行
     * @return 待持久化实体（未保存）
     */
    private SupplyRequest buildPurchaseOrderEntityFromItem(
        String busNo,
        CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand item) {

        String typeForRow = item.getTypeEnum();
        if (typeForRow == null || typeForRow.trim().isEmpty()) {
            typeForRow = SupplyType.CABINET_PURCHASE_ORDER.getCode();
        }

        SupplyRequest entity = this.buildCommonEntity(busNo, typeForRow, item.getItemTable(), item.getItemId(),
            item.getItemQuantity(), item.getUnitCode(), item.getCategoryEnum(), item.getSourceTypeEnum(), item.getSourceLocationId(),
            item.getPurposeTypeEnum(), item.getPurposeLocationId(), item.getApplicantId(), item.getApplyTime());
        // 可选上游：科室采购申请单号；空白表示手工直建
        if (item.getOriginalBusNo() != null && !item.getOriginalBusNo().trim().isEmpty()) {
            entity.setOriginalBusNo(item.getOriginalBusNo().trim());
        }
        entity.setSupplierId(item.getSupplierId());
        entity.setOccurrenceTime(item.getOccurrenceTime());
        entity.setPractitionerId(item.getPractitionerId());
        entity.setReason(item.getReason());
        entity.setRemake(item.getRemake());
        entity.setPrice(item.getPrice());
        entity.setTotalPrice(item.getTotalPrice());
        entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        return entity;
    }

    /**
     * 新建订货时校验可选 {@code original_bus_no}：空白跳过；非空须为已同意科室采购申请且未被未删除订货占用。
     *
     * @param originalBusNo            来源科室采购申请单号；可 blank
     * @param excludePurchaseOrderBusNo 改单占用复检时排除的本订货单号；新建传 null
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_106} 不可引用时
     */
    private void assertOptionalDeptPurchaseRequestLinkForPurchaseOrder(
        String originalBusNo,
        String excludePurchaseOrderBusNo) {

        if (originalBusNo == null || originalBusNo.trim().isEmpty()) {
            return;
        }
        String trimmedOriginal = originalBusNo.trim();
        List<SupplyRequest> upstreamLines = supplyRequestRepository.findByBusNoAndIsDeletedFalse(trimmedOriginal);
        if (upstreamLines == null || upstreamLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_106, "科室采购申请不存在或已删除，无法关联订货");
        }
        for (SupplyRequest upstream : upstreamLines) {
            if (upstream == null) {
                continue;
            }
            // 类型须为科室采购申请
            if (!SupplyType.isDeptPurchaseRequestTypeCode(upstream.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_PO_106, "来源单不是科室采购申请，无法关联订货");
            }
            // 仅已同意申请可被订货拉源
            if (!SupplyStatus.AGREE.getCode().equals(upstream.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_PO_106, "科室采购申请未同意，无法关联订货");
            }
        }
        boolean occupied;
        if (excludePurchaseOrderBusNo != null && !excludePurchaseOrderBusNo.trim().isEmpty()) {
            occupied = supplyRequestRepository.existsByOriginalBusNoAndTypeEnumAndBusNoNot(
                trimmedOriginal,
                SupplyType.CABINET_PURCHASE_ORDER.getCode(),
                excludePurchaseOrderBusNo.trim());
        } else {
            occupied = supplyRequestRepository.existsByOriginalBusNoAndTypeEnum(
                trimmedOriginal,
                SupplyType.CABINET_PURCHASE_ORDER.getCode());
        }
        // 一申请至多一张未删除订货（BR-WHS-PO-012）
        if (occupied) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_106, "科室采购申请已被其它订货单引用");
        }
    }

    /**
     * 创建或修订科室采购申请单（{@link SupplyType#DEPT_PURCHASE_REQUEST}）：产品定义级明细、单端点科室药房、同意仅终态。
     *
     * <p><b>修订路径</b>：首行既有 {@code busNo} → {@link SupplyRequest#assertDeptPurchaseRequestLinesRevisable(List)}
     * + 乐观锁 → 软删旧行后全量重插；<strong>不</strong>发布 {@link SupplyRequestCreatedEvent}。</p>
     *
     * <p>不写供应商、不写 {@code source_*}；不编排订货/库存。</p>
     *
     * @param command 申请明细与操作人
     * @return 业务单号（新建为新号，修订为既有号）
     * @throws BusinessException 类型/状态/乐观锁/必填位置不满足时
     */
    public String createDeptPurchaseRequest(CreateDeptPurchaseRequestCommand command) {
        // 前置：协商/参考单价刻度与行总额（与订货申请同构，BR-MON-001）
        this.writePricingSupport.applyDeptPurchaseRequestCommand(command);
        List<CreateDeptPurchaseRequestCommand.CreateDeptPurchaseRequestItemCommand> items = command.resolveItems();
        CreateDeptPurchaseRequestCommand.CreateDeptPurchaseRequestItemCommand head = items.get(0);
        String requestedBusNo = head.getBusNo();
        boolean revisionRequested = requestedBusNo != null && !requestedBusNo.trim().isEmpty();

        if (revisionRequested) {
            // 修订路径：首行带 busNo，逻辑删旧行后全量重插
            String busNo = requestedBusNo.trim();
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNoAndIsDeletedFalse(busNo);
            SupplyRequest.assertDeptPurchaseRequestLinesRevisable(existingLines);

            Integer expectedVer = command.getExpectedRowVersionForRevision();
            if (expectedVer == null) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "修订科室采购申请必须携带乐观锁版本 expectedRowVersionForRevision");
            }
            for (SupplyRequest existingLine : existingLines) {
                if (existingLine == null) {
                    continue;
                }
                Integer ver = existingLine.getVersion();
                if (ver == null || !expectedVer.equals(ver)) {
                    throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                        "科室采购申请已被修改或版本不匹配，请刷新后重试");
                }
            }

            this.softDeleteExistingLinesForRevision(existingLines);

            List<SupplyRequest> toInsert = new ArrayList<>();
            for (CreateDeptPurchaseRequestCommand.CreateDeptPurchaseRequestItemCommand item : items) {
                // 空白 type 由领域固定为科室采购申请，禁止其它类型混入
                if (item.getTypeEnum() == null || item.getTypeEnum().trim().isEmpty()) {
                    item.setTypeEnum(SupplyType.DEPT_PURCHASE_REQUEST.getCode());
                }
                if (!SupplyType.isDeptPurchaseRequestTypeCode(item.getTypeEnum())) {
                    throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                        "createDeptPurchaseRequest 仅允许科室采购申请类 type_enum");
                }
                SupplyRequest entity = this.buildDeptPurchaseRequestEntityFromItem(busNo, item);
                entity.assertCreatableMinimumFields();
                toInsert.add(entity);
            }
            supplyRequestRepository.saveAll(toInsert);
            return busNo;
        }

        String typeForBusNo = head.getTypeEnum();
        if (typeForBusNo == null || typeForBusNo.isBlank()) {
            typeForBusNo = SupplyType.DEPT_PURCHASE_REQUEST.getCode();
        }
        String busNo = this.generateSupplyBusNo(typeForBusNo);

        // 新建路径：生成新 busNo 并批量插入待审核行
        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreateDeptPurchaseRequestCommand.CreateDeptPurchaseRequestItemCommand item : items) {
            if (item.getTypeEnum() == null || item.getTypeEnum().trim().isEmpty()) {
                item.setTypeEnum(SupplyType.DEPT_PURCHASE_REQUEST.getCode());
            }
            if (!SupplyType.isDeptPurchaseRequestTypeCode(item.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "createDeptPurchaseRequest 仅允许科室采购申请类 type_enum");
            }
            SupplyRequest entity = this.buildDeptPurchaseRequestEntityFromItem(busNo, item);
            entity.assertCreatableMinimumFields();
            toSave.add(entity);
        }

        List<SupplyRequest> saved = supplyRequestRepository.saveAll(toSave);
        this.publishCreated(saved.get(0), command.getOperatorId(), items.size());
        return busNo;
    }

    /**
     * 由科室采购申请命令行组装供应请求实体（单端点 purpose；禁止 supplier / source_*）。
     *
     * @param busNo 业务单号
     * @param item  命令行
     * @return 待持久化实体（未保存）
     * @throws BusinessException 科室药房或类型非法
     */
    private SupplyRequest buildDeptPurchaseRequestEntityFromItem(
        String busNo,
        CreateDeptPurchaseRequestCommand.CreateDeptPurchaseRequestItemCommand item) {

        if (item.getPurposeTypeEnum() == null || item.getPurposeTypeEnum().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "科室药房类型不能为空");
        }
        if (item.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "科室药房不能为空");
        }

        String typeForRow = item.getTypeEnum();
        if (typeForRow == null || typeForRow.trim().isEmpty()) {
            typeForRow = SupplyType.DEPT_PURCHASE_REQUEST.getCode();
        }

        SupplyRequest entity = new SupplyRequest();
        entity.setBusNo(busNo);
        entity.setTypeEnum(typeForRow);
        entity.setItemTable(item.getItemTable());
        entity.setItemId(item.getItemId());
        entity.setItemQuantity(item.getItemQuantity());
        entity.setUnitCode(item.getUnitCode());
        entity.setPrice(item.getPrice());
        entity.setTotalPrice(item.getTotalPrice());

        if (item.getCategoryEnum() == null) {
            entity.setCategoryEnum(SupplyCategory.STOCK_SUPPLY.getCode());
        } else {
            entity.setCategoryEnum(item.getCategoryEnum());
        }

        entity.setPurposeTypeEnum(item.getPurposeTypeEnum().trim());
        entity.setPurposeLocationId(item.getPurposeLocationId());
        entity.setApplicantId(item.getApplicantId());
        entity.setPractitionerId(item.getPractitionerId());
        entity.setApplyTime(item.getApplyTime());
        entity.setOccurrenceTime(item.getOccurrenceTime());
        entity.setReason(item.getReason());
        entity.setRemake(item.getRemake());
        entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        return entity;
    }

    /**
     * 创建或修订药库进货单（{@link SupplyType#CABINET_INBOUND_RECEIPT}）：首行携带既有 {@code busNo} 时为同号全量明细替换（软删旧行后重插）。
     *
     * <p>修订语义对齐订货单 {@link #createPurchaseOrder(CreatePurchaseOrderCommand)}（AS WHS-IN-SDD-R-002）；门控见
     * {@link SupplyRequest#assertInboundReceiptLinesRevisable(List)}；须携带
     * {@link CreateInboundReceiptCommand#getExpectedRowVersionForRevision()} 与同号各行 {@code version} 对齐以防并发覆盖。</p>
     *
     * <p>新建路径在整批落库后发布 {@link SupplyRequestCreatedEvent}；修订路径不重复发布 Created（与订货修订口径一致）。</p>
     *
     * @param command 进货批量命令；各行 {@code typeEnum} 等须为持久化 code（见 {@link CreateInboundReceiptCommand}）
     * @return 生效的 {@code busNo}（新建为新号，修订为入参单号）
     * @throws BusinessException {@link ErrorCodeConstants#SUPPLY_REQUEST_INVALID_ARGUMENT} 修订缺版本、门控失败等；
     *                            {@link ErrorCodeConstants#WHS_IN_105} 乐观锁版本不匹配（进货单场景对外码）
     */
    public String createInboundReceipt(CreateInboundReceiptCommand command) {
        // 前置：进货协商单价刻度（价表尚未落库，BR-MON-001）
        this.writePricingSupport.applyInboundReceiptCommand(command);
        List<CreateInboundReceiptCommand.CreateInboundReceiptItemCommand> items = command.resolveItems();
        CreateInboundReceiptCommand.CreateInboundReceiptItemCommand head = items.get(0);
        String requestedBusNo = head.getBusNo();
        boolean revisionRequested;
        if (requestedBusNo != null && !requestedBusNo.trim().isEmpty()) {
            revisionRequested = true;
        } else {
            revisionRequested = false;
        }

        if (revisionRequested) {
            // 同号修订：先校验类型与状态门控，再整单软删旧行后批量重插，避免明细与审批态漂移。
            String busNo = requestedBusNo.trim();
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNoAndIsDeletedFalse(busNo);
            SupplyRequest.assertInboundReceiptLinesRevisable(existingLines);

            Integer expectedVer = command.getExpectedRowVersionForRevision();
            if (expectedVer == null) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "修订进货单必须携带乐观锁版本 expectedRowVersionForRevision");
            }
            // 乐观锁：同号下各行 version 须与前端提交的期望版本一致，防止覆盖他人已保存内容。
            for (SupplyRequest existingLine : existingLines) {
                if (existingLine == null) {
                    continue;
                }
                Integer ver = existingLine.getVersion();
                if (ver == null || !expectedVer.equals(ver)) {
                    throw new BusinessException(ErrorCodeConstants.WHS_IN_105, "进货单已被修改或版本不匹配，请刷新后重试");
                }
            }

            // 先删后增：逻辑删除旧行并 flush，再由后续 saveAll 重插同号明细
            this.softDeleteExistingLinesForRevision(existingLines);

            List<SupplyRequest> toInsert = new ArrayList<>();
            for (CreateInboundReceiptCommand.CreateInboundReceiptItemCommand item : items) {
                SupplyRequest entity = this.buildInboundReceiptEntityFromItem(busNo, item);
                entity.assertCreatableMinimumFields();
                toInsert.add(entity);
            }
            supplyRequestRepository.saveAll(toInsert);
            return busNo;
        }

        // 新建：生成单号后一次性批量插入全部明细行，再发布创建事件（事件在 publishCreated 内按首行触发）。
        String typeForBusNo = head.getTypeEnum();
        if (typeForBusNo == null || typeForBusNo.isBlank()) {
            typeForBusNo = SupplyType.CABINET_INBOUND_RECEIPT.getCode();
        }
        // —— 同一订货单仅允许一张未删除进货单引用（BR-WHS-IN-012；不区分进货单审批状态）。——
        this.assertPurchaseOrderNotReferencedByOtherInboundReceipt(head.getOriginalBusNo(), null);
        String busNo = this.generateSupplyBusNo(typeForBusNo);
        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreateInboundReceiptCommand.CreateInboundReceiptItemCommand item : items) {
            SupplyRequest entity = this.buildInboundReceiptEntityFromItem(busNo, item);
            entity.assertCreatableMinimumFields();
            toSave.add(entity);
        }
        List<SupplyRequest> saved = supplyRequestRepository.saveAll(toSave);
        this.publishCreated(saved.get(0), command.getOperatorId(), items.size());
        return busNo;
    }

    /**
     * 由进货命令项装配一行聚合实体（新建与修订重插共用）。
     *
     * @param busNo 业务单号
     * @param item  命令行
     * @return 待持久化实体
     */
    private SupplyRequest buildInboundReceiptEntityFromItem(String busNo, CreateInboundReceiptCommand.CreateInboundReceiptItemCommand item) {
        // 行级类型缺省时强制为药库进货单，防止命令项漏传 type 时落错供应类型。
        String typeForRow = item.getTypeEnum();
        if (typeForRow == null || typeForRow.trim().isEmpty()) {
            typeForRow = SupplyType.CABINET_INBOUND_RECEIPT.getCode();
        }
        SupplyRequest entity = this.buildCommonEntity(busNo, typeForRow, item.getItemTable(), item.getItemId(),
            item.getItemQuantity(), item.getUnitCode(), item.getCategoryEnum(), item.getSourceTypeEnum(), item.getSourceLocationId(),
            item.getPurposeTypeEnum(), item.getPurposeLocationId(), item.getApplicantId(), item.getApplyTime());
        entity.setOriginalBusNo(item.getOriginalBusNo());
        entity.setSupplierId(item.getSupplierId());
        entity.setLotNumber(item.getLotNumber());
        entity.setTraceNo(item.getTraceNo());
        entity.setPrice(item.getPrice());
        entity.setTotalPrice(item.getTotalPrice());
        entity.setOccurrenceTime(item.getOccurrenceTime());
        entity.setPractitionerId(item.getPractitionerId());
        entity.setStartTime(item.getStartTime());
        entity.setEndTime(item.getEndTime());
        entity.setReason(item.getReason());
        entity.setRemake(item.getRemake());
        entity.setInvoiceNo(item.getInvoiceNo());
        entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        return entity;
    }

    /**
     * 门控：同一订货单仅允许被一张未删除药库进货单以 {@code original_bus_no} 引用（BR-WHS-IN-012）。
     *
     * @param purchaseOrderBusNo         订货业务单号，可 blank（空白则跳过）
     * @param excludeInboundReceiptBusNo 改单时排除的本进货单号，可 blank
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_002} 已被其它进货单占用
     */
    private void assertPurchaseOrderNotReferencedByOtherInboundReceipt(
        String purchaseOrderBusNo,
        String excludeInboundReceiptBusNo) {
        if (purchaseOrderBusNo == null || purchaseOrderBusNo.isBlank()) {
            return;
        }
        boolean referenced;
        if (excludeInboundReceiptBusNo != null && !excludeInboundReceiptBusNo.isBlank()) {
            referenced = supplyRequestRepository.existsByOriginalBusNoAndTypeEnumAndBusNoNot(
                purchaseOrderBusNo.trim(),
                SupplyType.CABINET_INBOUND_RECEIPT.getCode(),
                excludeInboundReceiptBusNo.trim());
        } else {
            referenced = supplyRequestRepository.existsByOriginalBusNoAndTypeEnum(
                purchaseOrderBusNo.trim(),
                SupplyType.CABINET_INBOUND_RECEIPT.getCode());
        }
        if (referenced) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_002, "该订货单已被其他进货单引用");
        }
    }

    /**
     * 创建或修订药库退货单（{@link SupplyType#CABINET_RETURN_TO_SUPPLIER}）：首行携带既有 {@code busNo} 时为全量明细替换（无乐观锁版本，由应用层禁止传入 rowVersion）。
     *
     * @param command 退货命令；{@code originalBusNo} 由命令项携带，领域层强制非空
     * @return 业务单号
     * @throws BusinessException 明细为空、修订态不符或 {@code originalBusNo} 缺失时
     */
    public String createReturnToSupplier(CreateReturnToSupplierCommand command) {
        // 前置：进货落库价或批号/目录权威单价重算行金额（BR-MON-001 / BR-PRICE-SRC-001）
        this.writePricingSupport.applyReturnToSupplierCommand(command);
        List<CreateReturnToSupplierCommand.CreateReturnToSupplierItemCommand> items = command.resolveItems();
        CreateReturnToSupplierCommand.CreateReturnToSupplierItemCommand head = items.get(0);
        String requestedBusNo = head.getBusNo();
        boolean revisionRequested = requestedBusNo != null && !requestedBusNo.trim().isEmpty();

        if (revisionRequested) {
            // 同号修订：校验类型与待审核态后整单软删旧行再批量重插（退货无 rowVersion 乐观锁）。
            String busNo = requestedBusNo.trim();
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNoAndIsDeletedFalse(busNo);
            SupplyRequest.assertReturnToSupplierLinesRevisable(existingLines);

            // 先删后增：逻辑删除旧行并 flush，再由后续 saveAll 重插同号明细
            this.softDeleteExistingLinesForRevision(existingLines);

            List<SupplyRequest> toInsert = new ArrayList<>();
            for (CreateReturnToSupplierCommand.CreateReturnToSupplierItemCommand item : items) {
                SupplyRequest entity = this.buildReturnToSupplierEntityFromItem(busNo, item);
                entity.assertCreatableMinimumFields();
                toInsert.add(entity);
            }
            supplyRequestRepository.saveAll(toInsert);
            return busNo;
        }

        // 新建：生成单号后批量插入全部明细行，再发布创建事件。
        String typeForBusNo = head.getTypeEnum();
        if (typeForBusNo == null || typeForBusNo.isBlank()) {
            typeForBusNo = SupplyType.CABINET_RETURN_TO_SUPPLIER.getCode();
        }
        String busNo = this.generateSupplyBusNo(typeForBusNo);
        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreateReturnToSupplierCommand.CreateReturnToSupplierItemCommand item : items) {
            SupplyRequest entity = this.buildReturnToSupplierEntityFromItem(busNo, item);
            entity.assertCreatableMinimumFields();
            toSave.add(entity);
        }
        List<SupplyRequest> saved = supplyRequestRepository.saveAll(toSave);
        this.publishCreated(saved.get(0), command.getOperatorId(), items.size());
        return busNo;
    }

    /**
     * 由退货命令项装配一行聚合实体（新建与修订重插共用）。
     *
     * @param busNo 业务单号
     * @param item  命令行
     * @return 待持久化实体
     * @throws BusinessException {@code originalBusNo} 为空时
     */
    private SupplyRequest buildReturnToSupplierEntityFromItem(String busNo, CreateReturnToSupplierCommand.CreateReturnToSupplierItemCommand item) {
        // 行级类型缺省时强制为药库退供应商，防止命令项漏传 type 时落错供应类型。
        String typeForRow = item.getTypeEnum();
        if (typeForRow == null || typeForRow.trim().isEmpty()) {
            typeForRow = SupplyType.CABINET_RETURN_TO_SUPPLIER.getCode();
        }
        SupplyRequest entity = this.buildCommonEntity(busNo, typeForRow, item.getItemTable(), item.getItemId(),
            item.getItemQuantity(), item.getUnitCode(), item.getCategoryEnum(), item.getSourceTypeEnum(), item.getSourceLocationId(),
            item.getPurposeTypeEnum(), item.getPurposeLocationId(), item.getApplicantId(), item.getApplyTime());
        entity.setOriginalBusNo(item.getOriginalBusNo());
        entity.setSupplierId(item.getSupplierId());
        entity.setPractitionerId(item.getPractitionerId());
        entity.setOccurrenceTime(item.getOccurrenceTime());
        entity.setReason(item.getReason());
        entity.setRemake(item.getRemake());
        entity.setInvoiceNo(item.getInvoiceNo());
        entity.setLotNumber(item.getLotNumber());
        entity.setTraceNo(item.getTraceNo());
        // —— 写价门禁已写入命令行 price/totalPrice，随实体落库供列表合计与详情槽价。——
        entity.setPrice(item.getPrice());
        entity.setTotalPrice(item.getTotalPrice());
        entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        if (entity.getOriginalBusNo() == null || entity.getOriginalBusNo().trim().isEmpty()) {
            // 退货必须绑定来源进货单，禁止无 original 的自由选品落库
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "退货单 originalBusNo 必填");
        }
        return entity;
    }

    /**
     * 创建药库出库执行单多行：共享 {@code busNo}，初态待审核，落库后发布创建事件。
     *
     * @param command 明细行、操作人；首行可带 busNo（空则生成）
     * @return 业务单号
     * @throws BusinessException 明细或不变式校验失败时
     */
    public String createStockOut(CreateStockOutCommand command) {
        // 前置：批号/目录权威单价重算行金额（BR-MON-001）
        this.writePricingSupport.applyStockOutCommand(command);
        List<CreateStockOutCommand.CreateStockOutItemCommand> items = command.resolveItems();
        CreateStockOutCommand.CreateStockOutItemCommand soHead = items.get(0);
        String typeForBusNo = soHead.getTypeEnum();
        if (typeForBusNo == null || typeForBusNo.isBlank()) {
            typeForBusNo = SupplyType.CABINET_STOCK_OUT.getCode();
        }
        String requestedBusNo = soHead.getBusNo();
        boolean revisionRequested = requestedBusNo != null && !requestedBusNo.trim().isEmpty();
        if (revisionRequested) {
            String busNo = requestedBusNo.trim();
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNoAndIsDeletedFalse(busNo);
            SupplyRequest.assertCabinetStockOutLinesRevisable(existingLines);
            // 先删后增：逻辑删除旧行并 flush，再由后续 saveAll 重插同号明细
            this.softDeleteExistingLinesForRevision(existingLines);
            List<SupplyRequest> toInsert = new ArrayList<>();
            for (CreateStockOutCommand.CreateStockOutItemCommand item : items) {
                SupplyRequest entity = this.buildStockOutEntityFromItem(busNo, item);
                entity.assertCreatableMinimumFields();
                toInsert.add(entity);
            }
            supplyRequestRepository.saveAll(toInsert);
            return busNo;
        }

        String busNo = this.generateSupplyBusNo(typeForBusNo);
        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreateStockOutCommand.CreateStockOutItemCommand item : items) {
            SupplyRequest entity = this.buildStockOutEntityFromItem(busNo, item);
            entity.assertCreatableMinimumFields();
            toSave.add(entity);
        }
        List<SupplyRequest> saved = supplyRequestRepository.saveAll(toSave);
        this.publishCreated(saved.get(0), command.getOperatorId(), items.size());
        return busNo;
    }

    /**
     * 由出库命令行装配实体：类型缺省回落为药库出库；批号/价税等随命令落库。
     *
     * @param busNo 业务单号
     * @param item  命令行
     * @return 待保存实体
     */
    private SupplyRequest buildStockOutEntityFromItem(String busNo, CreateStockOutCommand.CreateStockOutItemCommand item) {
        String rowType = item.getTypeEnum();
        if (rowType == null || rowType.isBlank()) {
            rowType = SupplyType.CABINET_STOCK_OUT.getCode();
        }
        SupplyRequest entity = this.buildCommonEntity(busNo, rowType, item.getItemTable(), item.getItemId(),
            item.getItemQuantity(), item.getUnitCode(), item.getCategoryEnum(), item.getSourceTypeEnum(), item.getSourceLocationId(),
            item.getPurposeTypeEnum(), item.getPurposeLocationId(), item.getApplicantId(), item.getApplyTime());
        entity.setOriginalBusNo(item.getOriginalBusNo());
        entity.setOccurrenceTime(item.getOccurrenceTime());
        entity.setPractitionerId(item.getPractitionerId());
        entity.setReason(item.getReason());
        entity.setRemake(item.getRemake());
        entity.setLotNumber(item.getLotNumber());
        entity.setTraceNo(item.getTraceNo());
        entity.setPrice(item.getPrice());
        entity.setTotalPrice(item.getTotalPrice());
        entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        return entity;
    }

    /**
     * 创建或修订退库单（药房退库 / 科室退库主动单；退库入库壳单仅新建）：带既有 busNo 且类型可修订时软删旧行整批重插；否则新建并发布创建事件。
     *
     * @param command 退库明细（须含 originalBusNo 等）
     * @return 业务单号
     * @throws BusinessException 类型不支持修订、单不存在或字段非法时
     */
    public String createReturn(CreateReturnCommand command) {
        // 前置：批号/目录权威单价重算行金额（含编排壳单，BR-MON-001）
        this.writePricingSupport.applyReturnCommand(command);
        List<CreateReturnCommand.CreateReturnItemCommand> items = command.resolveItems();
        CreateReturnCommand.CreateReturnItemCommand head = items.get(0);
        String requestedBusNo = head.getBusNo();
        boolean revisionRequested = requestedBusNo != null && !requestedBusNo.trim().isEmpty();

        if (revisionRequested) {
            String busNo = requestedBusNo.trim();
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNoAndIsDeletedFalse(busNo);
            if (existingLines.isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "退库单不存在，无法修订");
            }
            SupplyRequest headExisting = existingLines.get(0);
            if (headExisting != null && SupplyType.PHARMACY_RETURN_TO_WAREHOUSE.getCode().equals(headExisting.getTypeEnum())) {
                SupplyRequest.assertPharmacyReturnToWarehouseLinesRevisable(existingLines);
            } else if (headExisting != null && SupplyType.CABINET_RETURN_STOCK_IN.getCode().equals(headExisting.getTypeEnum())) {
                // 退库入库单仅由下游退库 agree 自动生成，禁止同号修订
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "退库入库单由药房/科室退库同意后系统自动生成，不支持修订");
            } else {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "该退库单据类型不支持通过 createReturn 修订");
            }

            // 先删后增：逻辑删除旧行并 flush，再由后续 saveAll 重插同号明细
            this.softDeleteExistingLinesForRevision(existingLines);

            List<SupplyRequest> toInsert = new ArrayList<>();
            for (CreateReturnCommand.CreateReturnItemCommand item : items) {
                SupplyRequest entity = this.buildReturnEntityFromItem(busNo, item);
                this.applyBootstrapReturnStatus(command, entity);
                entity.assertCreatableMinimumFields();
                toInsert.add(entity);
            }
            supplyRequestRepository.saveAll(toInsert);
            return busNo;
        }

        String busNo = this.generateSupplyBusNo(items.get(0).getTypeEnum());

        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreateReturnCommand.CreateReturnItemCommand item : items) {
            SupplyRequest entity = this.buildReturnEntityFromItem(busNo, item);
            this.applyBootstrapReturnStatus(command, entity);
            entity.assertCreatableMinimumFields();
            toSave.add(entity);
        }

        List<SupplyRequest> saved = supplyRequestRepository.saveAll(toSave);
        this.publishCreated(saved.get(0), command.getOperatorId(), items.size());
        return busNo;
    }

    /**
     * 退库命令单行 → {@link SupplyRequest} 实体（含 originalBusNo Gate 与待审核初态）。
     *
     * @param busNo 业务单号
     * @param item  退库明细行命令
     * @return 待 save 实体
     * @throws BusinessException originalBusNo 缺失
     */
    private SupplyRequest buildReturnEntityFromItem(String busNo, CreateReturnCommand.CreateReturnItemCommand item) {
        SupplyRequest entity = this.buildCommonEntity(busNo, item.getTypeEnum(), item.getItemTable(), item.getItemId(),
            item.getItemQuantity(), item.getUnitCode(), item.getCategoryEnum(), item.getSourceTypeEnum(), item.getSourceLocationId(),
            item.getPurposeTypeEnum(), item.getPurposeLocationId(), item.getApplicantId(), item.getApplyTime());
        entity.setOriginalBusNo(item.getOriginalBusNo());
        entity.setSupplierId(item.getSupplierId());
        entity.setLotNumber(item.getLotNumber());
        entity.setTraceNo(item.getTraceNo());
        entity.setPrice(item.getPrice());
        entity.setTotalPrice(item.getTotalPrice());
        entity.setOccurrenceTime(item.getOccurrenceTime());
        entity.setReason(item.getReason());
        // 经手人：与制单人（applicantId）分离，由应用层从请求经手人下拉写入
        entity.setPractitionerId(item.getPractitionerId());
        entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        // 退库入库壳单：审批人/审批时刻随命令带入（bootstrap AGREE 不走 approvalToAgree）
        if (item.getApproverId() != null) {
            // 壳单 bootstrap：与下游退库同意人对齐，供侧栏审批人回显
            entity.setApproverId(item.getApproverId());
        }
        if (item.getApprovalTime() != null) {
            // 壳单 bootstrap：审批时刻随命令写入，避免 AGREE 行审批时间为空
            entity.setApprovalTime(item.getApprovalTime());
        }

        // 前置校验：退库/壳单 originalBusNo 必填（主动退库绑入库单；壳单绑退库单）
        if (entity.getOriginalBusNo() == null || entity.getOriginalBusNo().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "退库单 originalBusNo 必填");
        }
        return entity;
    }

    /**
     * 若命令携带 {@link CreateReturnCommand#getBootstrapFinalStatusCode()}，则在默认初态写入后覆盖为指定终态。
     *
     * @param command 领域命令
     * @param entity  即将校验并保存的实体
     */
    private void applyBootstrapReturnStatus(CreateReturnCommand command, SupplyRequest entity) {
        if (command == null || entity == null) {
            return;
        }
        String boot = command.getBootstrapFinalStatusCode();
        if (boot == null || boot.trim().isEmpty()) {
            return;
        }
        String code = boot.trim();
        EnumUtil.fromCode(SupplyStatus.class, code);
        entity.setStatusEnum(code);
    }

    /**
     * 创建损益执行单多行：审批通过后由应用层驱动发放与库存；初态待审核。
     *
     * <p><b>修订路径</b>：首行传入既有 {@code busNo} 时，对该单号下未删除行批量逻辑删除后按命令全量重插（仍为同一 {@code busNo}）。
     * 门控 {@link SupplyRequest#assertGainLossLinesRevisable(List)}；修订路径<strong>不</strong>发布
     * {@link com.openhis.health.domain.workflow.supplyrequest.event.SupplyRequestCreatedEvent}。</p>
     *
     * @param command 损益明细与操作人
     * @return 业务单号（新建为新号，修订为既有号）
     * @throws BusinessException 不变式失败时
     */
    public String createGainLoss(CreateGainLossCommand command) {
        // 前置：权威单价重算实盘/盈亏金额（BR-MON-001）
        this.writePricingSupport.applyGainLossCommand(command);
        List<CreateGainLossCommand.CreateGainLossItemCommand> items = command.resolveItems();
        CreateGainLossCommand.CreateGainLossItemCommand head = items.get(0);
        String requestedBusNo = head.getBusNo();
        boolean revisionRequested = requestedBusNo != null && !requestedBusNo.trim().isEmpty();

        if (revisionRequested) {
            // 修订路径：首行带 busNo，逻辑删旧行后全量重插
            String busNo = requestedBusNo.trim();
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNoAndIsDeletedFalse(busNo);
            SupplyRequest.assertGainLossLinesRevisable(existingLines);

            // 先删后增：逻辑删除旧行并 flush，再由后续 saveAll 重插同号明细
            this.softDeleteExistingLinesForRevision(existingLines);

            List<SupplyRequest> toInsert = new ArrayList<>();
            for (CreateGainLossCommand.CreateGainLossItemCommand item : items) {
                if (!SupplyType.isGainLossTypeCode(item.getTypeEnum())) {
                    throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                        "createGainLoss 仅允许损益类 type_enum");
                }
                // 业务含义：全量替换损益明细，盈亏/实盘字段口径与新建一致。
                SupplyRequest entity = this.buildGainLossEntityFromItem(busNo, item);
                entity.assertCreatableMinimumFields();
                toInsert.add(entity);
            }
            supplyRequestRepository.saveAll(toInsert);
            return busNo;
        }

        String busNo = this.generateSupplyBusNo(head.getTypeEnum());

        // 新建路径：生成新 busNo 并批量插入待审核行
        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreateGainLossCommand.CreateGainLossItemCommand item : items) {
            if (!SupplyType.isGainLossTypeCode(item.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "createGainLoss 仅允许损益类 type_enum");
            }
            // 业务含义：创建损益执行单（审批同意后由应用层按行盈亏数量对库存做加减，非零差异才发放）。
            SupplyRequest entity = this.buildGainLossEntityFromItem(busNo, item);
            entity.assertCreatableMinimumFields();
            toSave.add(entity);
        }

        List<SupplyRequest> saved = supplyRequestRepository.saveAll(toSave);
        this.publishCreated(saved.get(0), command.getOperatorId(), items.size());
        return busNo;
    }

    /**
     * 创建科室领用执行单多行：单端点科室药房扣减；审批同意后由应用层驱动出库发放与库存减少。
     *
     * <p><b>修订路径</b>：首行传入既有 {@code busNo} 时，对该单号下未删除行批量逻辑删除后按命令全量重插（仍为同一 {@code busNo}）。
     * 门控 {@link SupplyRequest#assertDeptIssueLinesRevisable(List)}；修订路径<strong>不</strong>发布
     * {@link com.openhis.health.domain.workflow.supplyrequest.event.SupplyRequestCreatedEvent}。</p>
     *
     * @param command 领用明细与操作人
     * @return 业务单号（新建为新号，修订为既有号）
     * @throws BusinessException 不变式失败时
     */
    public String createDeptIssue(CreateDeptIssueCommand command) {
        // 前置：批号/目录权威单价重算行金额（BR-MON-001）
        this.writePricingSupport.applyDeptIssueCommand(command);
        List<CreateDeptIssueCommand.CreateDeptIssueItemCommand> items = command.resolveItems();
        CreateDeptIssueCommand.CreateDeptIssueItemCommand head = items.get(0);
        String requestedBusNo = head.getBusNo();
        boolean revisionRequested = requestedBusNo != null && !requestedBusNo.trim().isEmpty();

        if (revisionRequested) {
            // 修订路径：首行带 busNo，逻辑删旧行后全量重插
            String busNo = requestedBusNo.trim();
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNoAndIsDeletedFalse(busNo);
            SupplyRequest.assertDeptIssueLinesRevisable(existingLines);

            // 先删后增：逻辑删除旧行并 flush，再由后续 saveAll 重插同号明细
            this.softDeleteExistingLinesForRevision(existingLines);

            List<SupplyRequest> toInsert = new ArrayList<>();
            for (CreateDeptIssueCommand.CreateDeptIssueItemCommand item : items) {
                if (!SupplyType.isDeptIssueTypeCode(item.getTypeEnum())) {
                    throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                        "createDeptIssue 仅允许科室领用类 type_enum");
                }
                // 业务含义：全量替换领用明细，数量须为正数领用量。
                SupplyRequest entity = this.buildDeptIssueEntityFromItem(busNo, item);
                entity.assertCreatableMinimumFields();
                toInsert.add(entity);
            }
            supplyRequestRepository.saveAll(toInsert);
            return busNo;
        }

        String busNo = this.generateSupplyBusNo(head.getTypeEnum());

        // 新建路径：生成新 busNo 并批量插入待审核行
        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreateDeptIssueCommand.CreateDeptIssueItemCommand item : items) {
            if (!SupplyType.isDeptIssueTypeCode(item.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "createDeptIssue 仅允许科室领用类 type_enum");
            }
            // 业务含义：创建科室领用执行单（审批同意后由应用层在 purpose 库位扣减库存）。
            SupplyRequest entity = this.buildDeptIssueEntityFromItem(busNo, item);
            entity.assertCreatableMinimumFields();
            toSave.add(entity);
        }

        List<SupplyRequest> saved = supplyRequestRepository.saveAll(toSave);
        this.publishCreated(saved.get(0), command.getOperatorId(), items.size());
        return busNo;
    }

    /**
     * 创建科室退领执行单多行：单端点科室药房加回；须关联已同意领用单 {@code original_bus_no}；审批同意后由应用层驱动入库发放与库存增加。
     *
     * <p><b>修订路径</b>：首行传入既有 {@code busNo} 时，对该单号下未删除行批量逻辑删除后按命令全量重插（仍为同一 {@code busNo}）。
     * 门控 {@link SupplyRequest#assertDeptIssueReturnLinesRevisable(List)}；修订路径<strong>不</strong>发布
     * {@link com.openhis.health.domain.workflow.supplyrequest.event.SupplyRequestCreatedEvent}。</p>
     *
     * @param command 退领明细与操作人
     * @return 业务单号（新建为新号，修订为既有号）
     * @throws BusinessException 不变式失败时
     */
    public String createDeptIssueReturn(CreateDeptIssueReturnCommand command) {
        // 前置：批号/目录权威单价重算行金额（BR-MON-001）
        this.writePricingSupport.applyDeptIssueReturnCommand(command);
        List<CreateDeptIssueReturnCommand.CreateDeptIssueReturnItemCommand> items = command.resolveItems();
        CreateDeptIssueReturnCommand.CreateDeptIssueReturnItemCommand head = items.get(0);
        String requestedBusNo = head.getBusNo();
        boolean revisionRequested = requestedBusNo != null && !requestedBusNo.trim().isEmpty();

        if (revisionRequested) {
            // 修订路径：首行带 busNo，逻辑删旧行后全量重插
            String busNo = requestedBusNo.trim();
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNoAndIsDeletedFalse(busNo);
            SupplyRequest.assertDeptIssueReturnLinesRevisable(existingLines);

            // 先删后增：逻辑删除旧行并 flush，再由后续 saveAll 重插同号明细
            this.softDeleteExistingLinesForRevision(existingLines);

            List<SupplyRequest> toInsert = new ArrayList<>();
            for (CreateDeptIssueReturnCommand.CreateDeptIssueReturnItemCommand item : items) {
                if (!SupplyType.isDeptIssueReturnTypeCode(item.getTypeEnum())) {
                    throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                        "createDeptIssueReturn 仅允许科室退领类 type_enum");
                }
                // 业务含义：全量替换退领明细，数量须为正数退回量。
                SupplyRequest entity = this.buildDeptIssueReturnEntityFromItem(busNo, item);
                entity.assertCreatableMinimumFields();
                toInsert.add(entity);
            }
            supplyRequestRepository.saveAll(toInsert);
            return busNo;
        }

        String busNo = this.generateSupplyBusNo(head.getTypeEnum());

        // 新建路径：生成新 busNo 并批量插入待审核行
        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreateDeptIssueReturnCommand.CreateDeptIssueReturnItemCommand item : items) {
            if (!SupplyType.isDeptIssueReturnTypeCode(item.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "createDeptIssueReturn 仅允许科室退领类 type_enum");
            }
            // 业务含义：创建科室退领执行单（审批同意后由应用层在 purpose 库位加回库存）。
            SupplyRequest entity = this.buildDeptIssueReturnEntityFromItem(busNo, item);
            entity.assertCreatableMinimumFields();
            toSave.add(entity);
        }

        List<SupplyRequest> saved = supplyRequestRepository.saveAll(toSave);
        this.publishCreated(saved.get(0), command.getOperatorId(), items.size());
        return busNo;
    }

    /**
     * 创建盘点执行单多行：审批通过后由应用层对差异入账；初态待审核。
     *
     * <p><b>修订路径</b>（AS-INV-WHS-007 / AS-INV-PHA-006，**WHS-ST-SDD-R-002**）：首行传入既有 {@code busNo} 时，
     * 对该单号下未删除行批量逻辑删除后按命令全量重插（仍为同一 {@code busNo}）。
     * 门控 {@link SupplyRequest#assertStocktakeLinesRevisable(List)}；修订路径<strong>不</strong>发布
     * {@link com.openhis.health.domain.workflow.supplyrequest.event.SupplyRequestCreatedEvent}（与订货/出库修订一致，避免误判为新单）。</p>
     *
     * @param command 盘点明细与操作人
     * @return 业务单号（新建为新号，修订为既有号）
     * @throws BusinessException 不变式失败时
     */
    public String createStocktake(CreateStocktakeCommand command) {
        // 前置：权威单价重算实盘/盈亏金额（BR-MON-001）
        this.writePricingSupport.applyStocktakeCommand(command);
        List<CreateStocktakeCommand.CreateStocktakeItemCommand> items = command.resolveItems();
        CreateStocktakeCommand.CreateStocktakeItemCommand head = items.get(0);
        String requestedBusNo = head.getBusNo();
        boolean revisionRequested = requestedBusNo != null && !requestedBusNo.trim().isEmpty();

        if (revisionRequested) {
            String busNo = requestedBusNo.trim();
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNoAndIsDeletedFalse(busNo);
            SupplyRequest.assertStocktakeLinesRevisable(existingLines);

            // 先删后增：逻辑删除旧行并 flush，再由后续 saveAll 重插同号明细
            this.softDeleteExistingLinesForRevision(existingLines);

            List<SupplyRequest> toInsert = new ArrayList<>();
            for (CreateStocktakeCommand.CreateStocktakeItemCommand item : items) {
                if (!SupplyType.isStocktakeTypeCode(item.getTypeEnum())) {
                    throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                        "createStocktake 仅允许盘点类 type_enum");
                }
                // 业务含义：全量替换盘点明细，盈亏/实盘字段口径与新建一致。
                SupplyRequest entity = this.buildStocktakeEntityFromItem(busNo, item);
                entity.assertCreatableMinimumFields();
                toInsert.add(entity);
            }
            supplyRequestRepository.saveAll(toInsert);
            return busNo;
        }

        String busNo = this.generateSupplyBusNo(head.getTypeEnum());

        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreateStocktakeCommand.CreateStocktakeItemCommand item : items) {
            if (!SupplyType.isStocktakeTypeCode(item.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "createStocktake 仅允许盘点类 type_enum");
            }
            // 业务含义：创建盘点执行单（审批同意后由应用层按行盈亏数量对库存做加减，非零差异才发放）。
            SupplyRequest entity = this.buildStocktakeEntityFromItem(busNo, item);
            entity.assertCreatableMinimumFields();
            toSave.add(entity);
        }

        List<SupplyRequest> saved = supplyRequestRepository.saveAll(toSave);
        this.publishCreated(saved.get(0), command.getOperatorId(), items.size());
        return busNo;
    }

    /**
     * 创建或修订库存期初执行单（AS-BAS-INV-001 · GAP-OPN-001）。
     *
     * <p>对齐 {@link #createGainLoss}/{@link #createStocktake}：免申请执行单据；仅写 {@code purpose_*}；
     * 初态待审核；同意后由应用层编排库存与追溯。允许空明细新建（仅预分配 {@code bus_no}，列表在补行后可见）。</p>
     *
     * <p><b>修订</b>：首行或命令携带既有 {@code busNo} 时软删旧行全量重插；若库中尚无行（空草稿首次补明细）则直接插入。</p>
     *
     * @param command 期初明细与操作人；空草稿须顶层 {@code typeEnum}/{@code purpose*}
     * @return 业务单号
     * @throws BusinessException 类型非 OPENING、字段非法或修订门控失败
     */
    public String createOpening(CreateOpeningCommand command) {
        // 前置：允许空明细（空草稿）；与 BatchCommand.resolveItems 双空拒绝区分
        List<CreateOpeningCommand.CreateOpeningItemCommand> items = command.resolveOpeningItemsAllowEmpty();
        String requestedBusNo = null;
        if (!items.isEmpty()) {
            CreateOpeningCommand.CreateOpeningItemCommand head = items.get(0);
            if (head.getBusNo() != null && !head.getBusNo().trim().isEmpty()) {
                requestedBusNo = head.getBusNo().trim();
            }
        }
        boolean revisionRequested = requestedBusNo != null;

        if (revisionRequested) {
            // 修订/空草稿补明细：同号全量替换
            String busNo = requestedBusNo;
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNoAndIsDeletedFalse(busNo);
            if (!existingLines.isEmpty()) {
                SupplyRequest.assertOpeningLinesRevisable(existingLines);
                this.softDeleteExistingLinesForRevision(existingLines);
            }
            if (items.isEmpty()) {
                // 修订为空明细：仅清空行，保留单号供后续再编
                return busNo;
            }
            List<SupplyRequest> toInsert = new ArrayList<>();
            for (CreateOpeningCommand.CreateOpeningItemCommand item : items) {
                if (!SupplyType.isOpeningTypeCode(item.getTypeEnum())) {
                    throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                        "createOpening 仅允许期初类 type_enum");
                }
                SupplyRequest entity = this.buildOpeningEntityFromItem(busNo, item, command.getOperatorId());
                entity.assertCreatableMinimumFields();
                toInsert.add(entity);
            }
            List<SupplyRequest> saved = supplyRequestRepository.saveAll(toInsert);
            // 空草稿首次落行：补发创建事件，便于列表/审计感知
            if (existingLines.isEmpty()) {
                this.publishCreated(saved.get(0), command.getOperatorId(), items.size());
            }
            return busNo;
        }

        // 新建：从首行或命令顶层解析类型
        String typeForSeq = command.getTypeEnum();
        if (typeForSeq == null || typeForSeq.isBlank()) {
            if (!items.isEmpty()) {
                typeForSeq = items.get(0).getTypeEnum();
            }
        }
        if (typeForSeq == null || typeForSeq.isBlank()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                "createOpening 须指定 typeEnum（空草稿顶层或明细首行）");
        }
        if (!SupplyType.isOpeningTypeCode(typeForSeq)) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                "createOpening 仅允许期初类 type_enum");
        }

        String busNo = this.generateSupplyBusNo(typeForSeq);
        if (items.isEmpty()) {
            // 空草稿：仅预留单号，不落行（补明细走修订路径）
            return busNo;
        }

        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreateOpeningCommand.CreateOpeningItemCommand item : items) {
            if (!SupplyType.isOpeningTypeCode(item.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "createOpening 仅允许期初类 type_enum");
            }
            SupplyRequest entity = this.buildOpeningEntityFromItem(busNo, item, command.getOperatorId());
            entity.assertCreatableMinimumFields();
            toSave.add(entity);
        }
        List<SupplyRequest> saved = supplyRequestRepository.saveAll(toSave);
        this.publishCreated(saved.get(0), command.getOperatorId(), items.size());
        return busNo;
    }

    /**
     * 将已同意期初单全行置为 {@link SupplyStatus#EXPIRED_INVALIDATED}（GAP-OPN-003 供应侧）。
     *
     * <p>不回滚库存/追溯；由应用层 {@code InventoryOpeningVoidOrchestrator} 先 decrease + T1 冲销再调本方法
     * （DOC-CONFLICT：AS 写 Domain 内聚门面；本方法仅本聚合状态迁移，符合 B.6）。</p>
     *
     * @param command 含 busNo 的作废命令
     * @return 业务单号
     * @throws BusinessException 非 AGREE 期初或单号不存在
     */
    public String voidOpening(VoidOpeningCommand command) {
        List<VoidOpeningCommand.VoidOpeningItemCommand> items = command.resolveItems();
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                "作废期初一次仅允许一张单据");
        }
        VoidOpeningCommand.VoidOpeningItemCommand item = items.get(0);
        if (item.getBusNo() == null || item.getBusNo().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "busNo 不能为空");
        }
        String busNo = item.getBusNo().trim();
        List<SupplyRequest> entities = supplyRequestRepository.findByBusNoAndIsDeletedFalse(busNo);
        if (entities.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "期初单不存在");
        }
        for (SupplyRequest entity : entities) {
            // 前置：仅当前有效 AGREE 期初可作废
            entity.markOpeningExpiredInvalidated();
            if (item.getReason() != null && !item.getReason().isBlank()) {
                entity.setReason(item.getReason().trim());
            }
        }
        supplyRequestRepository.saveAll(entities);
        return busNo;
    }

    /**
     * 创建调拨执行单多行：源库位与目的库位须不同；默认初态待审核。
     *
     * <p><b>修订</b>：首行 {@code busNo} 非空且存量为 {@link SupplyType#CABINET_TRANSFER_OUT} 时同号全量替换（
     * {@link SupplyRequest#assertCabinetTransferOutLinesRevisable}），不发布 Created 事件。</p>
     *
     * <p><b>壳单</b>：命令 {@link CreateTransferCommand#getBootstrapFinalStatusCode()} 为 {@code AGREE} 时直接落已同意态，
     * 供调出 agree 后生成对侧调入台账（库存不在此二次落账）。
     * 壳单行须携带 {@code approverId}/{@code approvalTime}（与调出同意人对齐；bootstrap 不走 {@code approvalToAgree}）。</p>
     *
     * @param command 调拨明细与操作人
     * @return 业务单号（新建为新号，修订/壳单为命令语义下的单号）
     * @throws BusinessException 源目相同、修订门控失败或字段非法时
     */
    public String createTransfer(CreateTransferCommand command) {
        // 前置：批号/目录权威单价重算行金额（BR-MON-001）
        this.writePricingSupport.applyTransferCommand(command);
        List<CreateTransferCommand.CreateTransferItemCommand> items = command.resolveItems();
        CreateTransferCommand.CreateTransferItemCommand head = items.get(0);
        String requestedBusNo = head.getBusNo();
        boolean revisionRequested = requestedBusNo != null && !requestedBusNo.trim().isEmpty();

        if (revisionRequested) {
            // 修订路径：软删旧行后全量重插，不发布 SupplyRequestCreatedEvent
            String busNo = requestedBusNo.trim();
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNoAndIsDeletedFalse(busNo);
            if (existingLines.isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "调拨单不存在，无法修订");
            }
            SupplyRequest headRow = existingLines.get(0);
            if (headRow != null && SupplyType.CABINET_TRANSFER_OUT.getCode().equals(headRow.getTypeEnum())) {
                SupplyRequest.assertCabinetTransferOutLinesRevisable(existingLines);
            } else {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "该调拨单据类型不支持通过 createTransfer 修订");
            }

            // 先删后增：逻辑删除旧行并 flush，再由后续 saveAll 重插同号明细
            this.softDeleteExistingLinesForRevision(existingLines);

            List<SupplyRequest> toInsert = new ArrayList<>();
            for (CreateTransferCommand.CreateTransferItemCommand item : items) {
                SupplyRequest entity = this.buildTransferEntityFromItem(busNo, item);
                this.applyBootstrapTransferStatus(command, entity);
                entity.assertCreatableMinimumFields();
                toInsert.add(entity);
            }
            supplyRequestRepository.saveAll(toInsert);
            return busNo;
        }

        String busNo = this.generateSupplyBusNo(head.getTypeEnum());

        // 新建路径：取号后批量插入；可选 bootstrap 覆盖终态（调入壳单）
        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreateTransferCommand.CreateTransferItemCommand item : items) {
            SupplyRequest entity = this.buildTransferEntityFromItem(busNo, item);
            this.applyBootstrapTransferStatus(command, entity);
            entity.assertCreatableMinimumFields();
            toSave.add(entity);
        }

        List<SupplyRequest> saved = supplyRequestRepository.saveAll(toSave);
        this.publishCreated(saved.get(0), command.getOperatorId(), items.size());
        return busNo;
    }

    /**
     * 若命令携带 {@link CreateTransferCommand#getBootstrapFinalStatusCode()}，则在默认初态写入后覆盖为指定终态。
     *
     * @param command 领域命令
     * @param entity  即将校验并保存的实体
     */
    private void applyBootstrapTransferStatus(CreateTransferCommand command, SupplyRequest entity) {
        if (command == null || entity == null) {
            return;
        }
        String boot = command.getBootstrapFinalStatusCode();
        if (boot == null || boot.trim().isEmpty()) {
            return;
        }
        String code = boot.trim();
        EnumUtil.fromCode(SupplyStatus.class, code);
        entity.setStatusEnum(code);
    }

    /**
     * 调拨命令单行 → {@link SupplyRequest} 实体（源/目标位置不可相同，初态待审核）。
     *
     * @param busNo 业务单号
     * @param item  调拨明细行命令
     * @return 待 save 实体
     * @throws BusinessException 源目标位置相同
     */
    private SupplyRequest buildTransferEntityFromItem(String busNo, CreateTransferCommand.CreateTransferItemCommand item) {
        SupplyRequest entity = this.buildCommonEntity(busNo, item.getTypeEnum(), item.getItemTable(), item.getItemId(),
            item.getItemQuantity(), item.getUnitCode(), item.getCategoryEnum(), item.getSourceTypeEnum(), item.getSourceLocationId(),
            item.getPurposeTypeEnum(), item.getPurposeLocationId(), item.getApplicantId(), item.getApplyTime());
        entity.setOriginalBusNo(item.getOriginalBusNo());
        entity.setOccurrenceTime(item.getOccurrenceTime());
        entity.setReason(item.getReason());
        entity.setLotNumber(item.getLotNumber());
        entity.setTraceNo(item.getTraceNo());
        entity.setSupplierId(item.getSupplierId());
        entity.setPrice(item.getPrice());
        entity.setTotalPrice(item.getTotalPrice());
        entity.setPractitionerId(item.getPractitionerId());
        entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        // 调出 agree 生成调入壳单：审批人/审批时刻随命令带入，与调出头对齐（bootstrap AGREE 不走 approvalToAgree）。
        if (item.getApproverId() != null) {
            // 壳单 bootstrap：与调出同意人对齐，供侧栏审批人回显
            entity.setApproverId(item.getApproverId());
        }
        if (item.getApprovalTime() != null) {
            // 壳单 bootstrap：审批时刻随命令写入，避免 AGREE 行审批时间为空
            entity.setApprovalTime(item.getApprovalTime());
        }

        if (entity.getSourceLocationId() != null && entity.getPurposeLocationId() != null) {
            if (entity.getSourceLocationId().equals(entity.getPurposeLocationId())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "调拨源位置与目标位置不能相同");
            }
        }
        return entity;
    }

    /**
     * createRequisition。
     *
     * <p>修订（PHA-QL-SDD-R-002）：首行携带既有 {@code busNo} 且存量为药房请领（{@link SupplyType#PHARMACY_REQUISITION}）时，
     * 软删同号未删除行后按命令明细整批重插； deliberately 不发布 {@link SupplyRequestCreatedEvent}（对齐订货单修订口径）。</p>
     *
     * @param command 请领/入库类明细；首行 busNo 空则新开单
     * @return 业务单号
     * @throws BusinessException 修订门控、类型不匹配或入库类缺 originalBusNo 时
     */
    public String createRequisition(CreateRequisitionCommand command) {
        // 前置：目录进价权威单价重算行金额（BR-MON-001）
        this.writePricingSupport.applyRequisitionCommand(command);
        List<CreateRequisitionCommand.CreateRequisitionItemCommand> items = command.resolveItems();
        CreateRequisitionCommand.CreateRequisitionItemCommand head = items.get(0);
        String requestedBusNo = head.getBusNo();
        boolean revisionRequested = requestedBusNo != null && !requestedBusNo.trim().isEmpty();

        if (revisionRequested) {
            String busNo = requestedBusNo.trim();
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNoAndIsDeletedFalse(busNo);
            if (existingLines.isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "单据不存在，无法修订");
            }
            SupplyRequest headRow = existingLines.get(0);
            if (headRow != null && SupplyType.PHARMACY_REQUISITION.getCode().equals(headRow.getTypeEnum())) {
                SupplyRequest.assertPharmacyRequisitionLinesRevisable(existingLines);
            } else if (headRow != null && SupplyType.PHARMACY_STOCK_IN.getCode().equals(headRow.getTypeEnum())) {
                SupplyRequest.assertPharmacyStockInLinesRevisable(existingLines);
            } else {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "该单据类型不支持通过 createRequisition 修订");
            }

            // 先删后增：逻辑删除旧行并 flush，再由后续 saveAll 重插同号明细
            this.softDeleteExistingLinesForRevision(existingLines);

            List<SupplyRequest> toInsert = new ArrayList<>();
            for (CreateRequisitionCommand.CreateRequisitionItemCommand item : items) {
                SupplyRequest entity = this.buildRequisitionEntityFromItem(busNo, item);
                this.applyBootstrapRequisitionStatus(command, entity);
                entity.assertCreatableMinimumFields();
                toInsert.add(entity);
            }
            supplyRequestRepository.saveAll(toInsert);
            return busNo;
        }

        String busNo = this.generateSupplyBusNo(items.get(0).getTypeEnum());

        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreateRequisitionCommand.CreateRequisitionItemCommand item : items) {
            SupplyRequest entity = this.buildRequisitionEntityFromItem(busNo, item);
            this.applyBootstrapRequisitionStatus(command, entity);
            entity.assertCreatableMinimumFields();
            toSave.add(entity);
        }

        List<SupplyRequest> saved = supplyRequestRepository.saveAll(toSave);
        this.publishCreated(saved.get(0), command.getOperatorId(), items.size());
        return busNo;
    }

    /**
     * 若命令携带 {@link CreateRequisitionCommand#getBootstrapFinalStatusCode()}，则在默认初态写入后覆盖为指定终态（须为合法 {@link SupplyStatus} 持久化 code）。
     *
     * @param command 领域命令
     * @param entity  即将校验并保存的实体
     */
    private void applyBootstrapRequisitionStatus(CreateRequisitionCommand command, SupplyRequest entity) {
        if (command == null || entity == null) {
            return;
        }
        String boot = command.getBootstrapFinalStatusCode();
        if (boot == null || boot.trim().isEmpty()) {
            return;
        }
        String code = boot.trim();
        EnumUtil.fromCode(SupplyStatus.class, code);
        entity.setStatusEnum(code);
    }

    /**
     * 请领/入库命令单行 → {@link SupplyRequest} 实体（科室入库须 originalBusNo，初态待审核）。
     *
     * @param busNo 业务单号
     * @param item  请领明细行命令
     * @return 待 save 实体
     * @throws BusinessException 科室入库缺 originalBusNo
     */
    private SupplyRequest buildRequisitionEntityFromItem(String busNo, CreateRequisitionCommand.CreateRequisitionItemCommand item) {
        SupplyRequest entity = this.buildCommonEntity(busNo, item.getTypeEnum(), item.getItemTable(), item.getItemId(),
            item.getItemQuantity(), item.getUnitCode(), item.getCategoryEnum(), item.getSourceTypeEnum(), item.getSourceLocationId(),
            item.getPurposeTypeEnum(), item.getPurposeLocationId(), item.getApplicantId(), item.getApplyTime());
        entity.setOriginalBusNo(item.getOriginalBusNo());
        entity.setSupplierId(item.getSupplierId());
        entity.setLotNumber(item.getLotNumber());
        entity.setTraceNo(item.getTraceNo());
        entity.setPrice(item.getPrice());
        entity.setTotalPrice(item.getTotalPrice());
        entity.setOccurrenceTime(item.getOccurrenceTime());
        entity.setPractitionerId(item.getPractitionerId());
        // 批号效期：与药库进货/出库明细同构，保存选品带回的生产日期与失效日期
        entity.setStartTime(item.getStartTime());
        entity.setEndTime(item.getEndTime());
        entity.setReason(item.getReason());
        entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        // 出库 agree 生成入库壳单：审批人/审批时刻随命令带入，与出库头对齐（bootstrap AGREE 不走 approvalToAgree）。
        if (item.getApproverId() != null) {
            entity.setApproverId(item.getApproverId());
        }
        if (item.getApprovalTime() != null) {
            entity.setApprovalTime(item.getApprovalTime());
        }

        // 手工药房入库（PHARMACY_STOCK_IN）允许 originalBusNo 为空；科室入库（DEPT_STOCK_IN）仍须绑定上游执行单号（AS-INV-PHA-001 / DM 口径）。
        if (SupplyType.DEPT_STOCK_IN.getCode().equals(entity.getTypeEnum())) {
            if (entity.getOriginalBusNo() == null || entity.getOriginalBusNo().trim().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "入库执行单 originalBusNo 必填");
            }
        }
        return entity;
    }

    /**
     * 创建药品汇总单明细多行：关联 summaryId、患者等，初态为 {@link SupplyStatus#PENDING_APPROVAL}（对齐库房单据保存草稿）。
     *
     * @param command 汇总明细与操作人
     * @return 业务单号
     * @throws BusinessException 不变式失败时
     */
    public String createSummarySupplyRequest(CreateSummarySupplyRequestCommand command) {
        List<CreateSummarySupplyRequestCommand.CreateSummarySupplyRequestItemCommand> items = command.resolveItems();
        CreateSummarySupplyRequestCommand.CreateSummarySupplyRequestItemCommand msHead = items.get(0);
        String typeForBusNo = msHead.getTypeEnum();
        if (typeForBusNo == null || typeForBusNo.isBlank()) {
            typeForBusNo = SupplyType.MEDICATION_SUMMARY_ORDER.getCode();
        }
        String busNo;
        if (msHead.getBusNo() != null && !msHead.getBusNo().isBlank()) {
            busNo = msHead.getBusNo().trim();
        } else {
            busNo = this.generateSupplyBusNo(typeForBusNo);
        }

        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreateSummarySupplyRequestCommand.CreateSummarySupplyRequestItemCommand item : items) {
            // 业务含义：创建药品汇总单明细行（初态待审核；apply_time 在 submitApproval 时写入）。
            SupplyRequest entity = this.buildCommonEntity(busNo, item.getTypeEnum(), item.getItemTable(), item.getItemId(),
                item.getItemQuantity(), item.getUnitCode(), item.getCategoryEnum(), null, null,
                item.getPurposeTypeEnum(), item.getPurposeLocationId(), item.getApplicantId(), null);
            entity.setSummaryId(item.getSummaryId());
            entity.setPatientId(item.getPatientId());
            entity.setLotNumber(item.getLotNumber());
            entity.setOccurrenceTime(item.getOccurrenceTime());
            entity.setReason(item.getReason());
            entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());

            entity.assertCreatableMinimumFields();
            toSave.add(entity);
        }

        List<SupplyRequest> saved = supplyRequestRepository.saveAll(toSave);
        this.publishCreated(saved.get(0), command.getOperatorId(), items.size());
        return busNo;
    }

    /**
     * 创建科室耗材类汇总/盘点单明细多行（如 type 28），初态待审核。
     *
     * @param command 明细与操作人
     * @return 业务单号
     * @throws BusinessException 不变式失败时
     */
    public String createDeviceSummarySupplyRequest(CreateDeviceSummarySupplyRequestCommand command) {
        List<CreateDeviceSummarySupplyRequestCommand.CreateDeviceSummarySupplyRequestItemCommand> items = command.resolveItems();
        CreateDeviceSummarySupplyRequestCommand.CreateDeviceSummarySupplyRequestItemCommand ddHead = items.get(0);
        String typeForBusNo = ddHead.getTypeEnum();
        if (typeForBusNo == null || typeForBusNo.isBlank()) {
            typeForBusNo = SupplyType.DEPT_DEVICE_STOCKTAKE.getCode();
        }
        String busNo = this.generateSupplyBusNo(typeForBusNo);

        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreateDeviceSummarySupplyRequestCommand.CreateDeviceSummarySupplyRequestItemCommand item : items) {
            // 业务含义：创建科室耗材盘点单明细行（type 28）。
            SupplyRequest entity = this.buildCommonEntity(busNo, item.getTypeEnum(), item.getItemTable(), item.getItemId(),
                item.getItemQuantity(), item.getUnitCode(), item.getCategoryEnum(), null, null,
                item.getPurposeTypeEnum(), item.getPurposeLocationId(), item.getApplicantId(), item.getApplyTime());
            entity.setOccurrenceTime(item.getOccurrenceTime());
            entity.setReason(item.getReason());
            entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());

            entity.assertCreatableMinimumFields();
            toSave.add(entity);
        }

        List<SupplyRequest> saved = supplyRequestRepository.saveAll(toSave);
        this.publishCreated(saved.get(0), command.getOperatorId(), items.size());
        return busNo;
    }

    /**
     * 提交审批：同一 {@code bus_no} 下全部明细批量进入审核中（{@link SupplyStatus#APPROVAL#getCode()}），并发布 {@link SupplyRequestSubmittedEvent}。
     *
     * @param command 批次内须恰好一项，且含非空 {@code busNo}
     * @return 成功时固定 {@code true}
     * @throws BusinessException {@link ErrorCodeConstants#SUPPLY_REQUEST_INVALID_ARGUMENT} 批次规模或参数非法；
     *                            {@link ErrorCodeConstants#SUPPLY_REQUEST_NOT_FOUND} 单据不存在；行级 {@link SupplyRequest#assertSubmittable()} 失败
     */
    public boolean submitApproval(SubmitApprovalCommand command) {
        List<SubmitApprovalCommand.SubmitApprovalItemCommand> items = command.resolveItems();
        // 前置校验：写命令一次仅处理一条，防止批量误写
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "提交审批一次仅允许提交一张单据");
        }
        SubmitApprovalCommand.SubmitApprovalItemCommand item = items.get(0);
        // 前置校验：业务单号不能为空，否则无法定位供应请求
        if (item.getBusNo() == null || item.getBusNo().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "busNo 不能为空");
        }

        // 业务含义：同 busNo 下所有明细行批量推进到 APPROVAL，并写入申请时刻（服务端当前时间）与申请人（会话医务人员）。
        List<SupplyRequest> entities = supplyRequestRepository.findByBusNoAndIsDeletedFalse(item.getBusNo().trim());
        // 按单号未命中任何行时拒绝，防止对幽灵单号审批
        if (entities.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "单据不存在");
        }

        Long applicantId = item.getApplicantId();
        // 必填入参缺失时拒绝继续，避免写脏数据
        if (applicantId == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "applicantId 不能为空");
        }
        LocalDateTime applyClock = LocalDateTime.now();
        for (SupplyRequest entity : entities) {
            entity.assertSubmittable();
            entity.setApplicantId(applicantId);
            entity.toApproval(applyClock);
        }

        List<SupplyRequest> saved = supplyRequestRepository.saveAll(entities);
        SupplyRequest first = saved.get(0);
        SupplyRequestSubmittedEvent submittedEvent = new SupplyRequestSubmittedEvent(first.getId(), first.getBusNo(), first.getTypeEnum(),
            first.getApplyTime(), first.getApplicantId(), command.getOperatorId());
        submittedEvent.setStatusEnum(first.getStatusEnum());
        submittedEvent.setTenantId(first.getTenantId());
        domainEventPublisher.publish(submittedEvent);
        return true;
    }

    /**
     * 撤回审批：同一 {@code bus_no} 下全部明细批量转为已撤回（{@link SupplyStatus#WITHDRAW#getCode()}），并发布 {@link SupplyRequestWithdrawnEvent}。
     *
     * @param command 批次内须恰好一项，且含非空 {@code busNo}
     * @return 成功时固定 {@code true}
     * @throws BusinessException {@link ErrorCodeConstants#SUPPLY_REQUEST_INVALID_ARGUMENT}；{@link ErrorCodeConstants#SUPPLY_REQUEST_NOT_FOUND}；
     *                            {@link SupplyRequest#assertWithdrawable()} 不满足时
     */
    public boolean withdrawApproval(WithdrawApprovalCommand command) {
        List<WithdrawApprovalCommand.WithdrawApprovalItemCommand> items = command.resolveItems();
        // 前置校验：写命令一次仅处理一条，防止批量误写
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "撤回审批一次仅允许提交一张单据");
        }
        WithdrawApprovalCommand.WithdrawApprovalItemCommand item = items.get(0);
        // 前置校验：业务单号不能为空，否则无法定位供应请求
        if (item.getBusNo() == null || item.getBusNo().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "busNo 不能为空");
        }

        // 业务含义：同 busNo 下所有明细行批量推进到 WITHDRAW。
        List<SupplyRequest> entities = supplyRequestRepository.findByBusNoAndIsDeletedFalse(item.getBusNo().trim());
        // 按单号未命中任何行时拒绝，防止对幽灵单号审批
        if (entities.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "单据不存在");
        }

        for (SupplyRequest entity : entities) {
            entity.assertWithdrawable();
            entity.toWithdraw();
        }

        List<SupplyRequest> saved = supplyRequestRepository.saveAll(entities);
        SupplyRequest first = saved.get(0);
        SupplyRequestWithdrawnEvent withdrawnEvent =
            new SupplyRequestWithdrawnEvent(first.getId(), first.getBusNo(), first.getTypeEnum(), command.getOperatorId());
        withdrawnEvent.setStatusEnum(first.getStatusEnum());
        withdrawnEvent.setTenantId(first.getTenantId());
        domainEventPublisher.publish(withdrawnEvent);
        return true;
    }

    /**
     * 审批同意：同一 {@code bus_no} 全行批量进入已同意（{@link SupplyStatus#AGREE#getCode()}），并发布 {@link SupplyRequestAgreedEvent}。
     *
     * <p>防重：若库内已存在 {@code status_enum =} {@link SupplyStatus#AGREE#getCode()} 行则拒绝重复同意。后续发放与库存由应用层编排。</p>
     *
     * @param command 批次内须恰好一项；{@code approverId} 领域必填
     * @return 成功时固定 {@code true}
     * @throws BusinessException {@link ErrorCodeConstants#SUPPLY_REQUEST_INVALID_ARGUMENT} 参数或重复同意；{@link ErrorCodeConstants#SUPPLY_REQUEST_NOT_FOUND}；
     *                            {@link SupplyRequest#assertApprovable()} 失败
     */
    public boolean agreeRequest(AgreeRequestCommand command) {
        List<AgreeRequestCommand.AgreeRequestItemCommand> items = command.resolveItems();
        // 前置校验：写命令一次仅处理一条，防止批量误写
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "审批同意一次仅允许提交一张单据");
        }
        AgreeRequestCommand.AgreeRequestItemCommand item = items.get(0);
        // 前置校验：业务单号不能为空，否则无法定位供应请求
        if (item.getBusNo() == null || item.getBusNo().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "busNo 不能为空");
        }
        // 审批人主键必填，否则无法审计同意/驳回操作者
        if (item.getApproverId() == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "approverId 不能为空");
        }

        // 防重：任一行已 AGREE 则拒绝（幂等门控由读侧 supplyRequestValidation 承接）。
        boolean hasAgreed = supplyRequestRepository.existsByBusNoAndStatusEnum(item.getBusNo().trim(), SupplyStatus.AGREE.getCode());
        // 财务审批族单据须校验财务审批岗位授权
        if (hasAgreed) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "单据已同意，禁止重复同意");
        }

        List<SupplyRequest> entities = supplyRequestRepository.findByBusNoAndIsDeletedFalse(item.getBusNo().trim());
        // 按单号未命中任何行时拒绝，防止对幽灵单号审批
        if (entities.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "单据不存在");
        }

        // Gate：财务审批族 type_enum 须持有财务审批岗位（订货/退库入库/调拨等不在白名单内）
        SupplyRequest firstForGate = entities.get(0);
        financeApprovalAuthorizer.assertFinanceApprovalForSupplyType(firstForGate.getTypeEnum());

        LocalDateTime approvalTime = LocalDateTime.now();
        for (SupplyRequest entity : entities) {
            entity.assertApprovable();
            entity.approvalToAgree(item.getApproverId(), approvalTime);
        }
        supplyRequestRepository.saveAll(entities);

        List<SupplyRequest> saved = supplyRequestRepository.findByBusNoAndIsDeletedFalse(item.getBusNo().trim());
        SupplyRequest first = saved.get(0);
        SupplyRequestAgreedEvent agreedEvent = new SupplyRequestAgreedEvent(first.getId(), first.getBusNo(), first.getTypeEnum(), first.getApproverId(),
            first.getApprovalTime(), command.getOperatorId());
        agreedEvent.setStatusEnum(first.getStatusEnum());
        agreedEvent.setTenantId(first.getTenantId());
        domainEventPublisher.publish(agreedEvent);
        return true;
    }

    /**
     * 审批驳回：同一 {@code bus_no} 全行批量进入驳回态，将 {@code changeReasonCode} 写入各行 {@code reason}，
     * 并发布 {@link SupplyRequestRejectedEvent}（载荷含变更原因）。
     *
     * @param command 批次内须恰好一项；{@code changeReasonCode} 为驳回原因文案（应用层来自 rejectReason/changeReason，可空）
     * @return 成功时固定 {@code true}
     * @throws BusinessException {@link ErrorCodeConstants#SUPPLY_REQUEST_INVALID_ARGUMENT}；{@link ErrorCodeConstants#SUPPLY_REQUEST_NOT_FOUND}；
     *                            {@link SupplyRequest#assertRejectable()} 失败
     */
    public boolean rejectRequest(RejectRequestCommand command) {
        List<RejectRequestCommand.RejectRequestItemCommand> items = command.resolveItems();
        // 前置校验：写命令一次仅处理一条，防止批量误写
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "审批驳回一次仅允许提交一张单据");
        }
        RejectRequestCommand.RejectRequestItemCommand item = items.get(0);
        // 前置校验：业务单号不能为空，否则无法定位供应请求
        if (item.getBusNo() == null || item.getBusNo().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "busNo 不能为空");
        }
        // 审批人主键必填，否则无法审计同意/驳回操作者
        if (item.getApproverId() == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "approverId 不能为空");
        }

        List<SupplyRequest> entities = supplyRequestRepository.findByBusNoAndIsDeletedFalse(item.getBusNo().trim());
        // 按单号未命中任何行时拒绝，防止对幽灵单号审批
        if (entities.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "单据不存在");
        }

        // Gate：财务审批族 type_enum 须持有财务审批岗位
        SupplyRequest firstForGate = entities.get(0);
        financeApprovalAuthorizer.assertFinanceApprovalForSupplyType(firstForGate.getTypeEnum());

        LocalDateTime approvalTime = LocalDateTime.now();
        // 驳回原因：应用层将 rejectReason/changeReason 写入 changeReasonCode，同号全行落 reason 供列表展示。
        String rejectReason = item.getChangeReasonCode();
        for (SupplyRequest entity : entities) {
            entity.assertRejectable();
            entity.approvalToReject(item.getApproverId(), approvalTime, rejectReason);
        }

        List<SupplyRequest> saved = supplyRequestRepository.saveAll(entities);
        SupplyRequest first = saved.get(0);
        SupplyRequestRejectedEvent rejectedEvent = new SupplyRequestRejectedEvent(first.getId(), first.getBusNo(), first.getTypeEnum(),
            first.getApproverId(), first.getApprovalTime(), item.getChangeReasonCode(), command.getOperatorId());
        rejectedEvent.setStatusEnum(first.getStatusEnum());
        rejectedEvent.setTenantId(first.getTenantId());
        domainEventPublisher.publish(rejectedEvent);
        return true;
    }

    /**
     * 按业务单号逻辑删除整张供应单全部行并发布 {@link SupplyRequestDeletedEvent}。
     *
     * <p>跨域「已产生下游发放/库存」须由应用层先校验（见方法内 DOC-CONFLICT 注释）。</p>
     *
     * @param command 批次内须恰好一项，且含非空 {@code busNo}
     * @return 成功时固定 {@code true}
     * @throws BusinessException {@link ErrorCodeConstants#SUPPLY_REQUEST_INVALID_ARGUMENT}（含 BR-SR-011 状态不允许删除）；
     *                            {@link ErrorCodeConstants#SUPPLY_REQUEST_NOT_FOUND}
     */
    public boolean removeByBusNo(RemoveByBusNoCommand command) {
        List<RemoveByBusNoCommand.RemoveByBusNoItemCommand> items = command.resolveItems();
        // 前置校验：写命令一次仅处理一条，防止批量误写
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "删除单据一次仅允许提交一张单据");
        }
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = items.get(0);
        // 前置校验：业务单号不能为空，否则无法定位供应请求
        if (item.getBusNo() == null || item.getBusNo().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "busNo 不能为空");
        }

        // DOC-CONFLICT: BR-SR-010 要求“禁止删除已产生下游发放/库存的单据”，该门控需要跨域校验（SupplyDelivery/InventoryItem）。
        // 领域层无法读取其他聚合数据；应用层应在调用前完成跨域校验。
        List<SupplyRequest> entities = supplyRequestRepository.findByBusNoAndIsDeletedFalse(item.getBusNo().trim());
        // 按单号未命中任何行时拒绝，防止对幽灵单号审批
        if (entities.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "单据不存在");
        }

        // 业务含义：同号多行须逐行满足 BR-SR-011（待审核/驳回/已撤回），避免部分行已推进仍被误删。
        for (SupplyRequest entity : entities) {
            entity.assertRemovableForSoftDelete();
        }

        for (SupplyRequest entity : entities) {
            entity.setIsDeleted(Boolean.TRUE);
        }
        List<SupplyRequest> saved = supplyRequestRepository.saveAll(entities);
        SupplyRequest first = saved.get(0);
        SupplyRequestDeletedEvent deletedEvent =
            new SupplyRequestDeletedEvent(first.getId(), first.getBusNo(), first.getTypeEnum(), command.getOperatorId());
        deletedEvent.setStatusEnum(first.getStatusEnum());
        deletedEvent.setTenantId(first.getTenantId());
        domainEventPublisher.publish(deletedEvent);
        return true;
    }

    /**
     * 按汇总编号列表取消药品汇总单行：仅处理 {@link SupplyType#MEDICATION_SUMMARY_ORDER}，逻辑删除并发布取消事件。
     *
     * @param command 批次内一组 summaryIdList 与操作人
     * @return 被逻辑删除行的主键列表（类型不匹配行跳过）
     * @throws BusinessException 批次或参数非法时
     */
    public List<Long> cancelSummarySupplyRequest(CancelSummarySupplyRequestCommand command) {
        List<CancelSummarySupplyRequestCommand.CancelSummarySupplyRequestItemCommand> items = command.resolveItems();
        // 前置校验：写命令一次仅处理一条，防止批量误写
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "取消汇总单一次仅允许提交一组汇总编号");
        }
        CancelSummarySupplyRequestCommand.CancelSummarySupplyRequestItemCommand item = items.get(0);
        // 业务条件分支：见相邻判断的业务后果
        if (item.getSummaryIdList() == null || item.getSummaryIdList().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "summaryIdList 不能为空");
        }

        // 业务含义：仅取消 type 27 的汇总单行（此处按 summaryIdList 批量逻辑删除）。
        List<SupplyRequest> entities = supplyRequestRepository.findBySummaryIdIn(item.getSummaryIdList());
        List<Long> affectedIdList = new ArrayList<>();
        for (SupplyRequest entity : entities) {
            // 必填入参缺失时拒绝继续，避免写脏数据
            if (entity == null) {
                continue;
            }
            // 业务条件分支：见相邻判断的业务后果
            if (!SupplyType.MEDICATION_SUMMARY_ORDER.getCode().equals(entity.getTypeEnum())) {
                continue;
            }
            entity.setIsDeleted(Boolean.TRUE);
            affectedIdList.add(entity.getId());
        }

        supplyRequestRepository.saveAll(entities);
        // 取首条受影响 ID 作为事件聚合标识；若无受影响记录则传 null。
        Long aggregateId;
        if (affectedIdList.isEmpty()) {
            aggregateId = null;
        } else {
            aggregateId = affectedIdList.get(0);
        }
        domainEventPublisher.publish(new SummarySupplyRequestCancelledEvent(aggregateId, item.getSummaryIdList(), command.getOperatorId()));
        return affectedIdList;
    }

    /**
     * 私有方法：publishCreated。
     */
    private void publishCreated(SupplyRequest first, Long operatorId, Integer lineCount) {
        SupplyRequestCreatedEvent event = new SupplyRequestCreatedEvent(first.getId(), first.getBusNo(), first.getTypeEnum(), first.getStatusEnum(), operatorId);
        event.setOriginalBusNo(first.getOriginalBusNo());
        event.setSummaryId(first.getSummaryId());
        event.setPatientId(first.getPatientId());
        event.setLineCount(lineCount);
        event.setTenantId(first.getTenantId());
        event.setCategoryEnum(first.getCategoryEnum());
        event.setSourceTypeEnum(first.getSourceTypeEnum());
        event.setSourceLocationId(first.getSourceLocationId());
        event.setPurposeTypeEnum(first.getPurposeTypeEnum());
        event.setPurposeLocationId(first.getPurposeLocationId());
        event.setItemTable(first.getItemTable());
        event.setItemId(first.getItemId());
        event.setItemQuantity(first.getItemQuantity());
        event.setUnitCode(first.getUnitCode());
        event.setLotNumber(first.getLotNumber());
        event.setPrice(first.getPrice());
        event.setTotalPrice(first.getTotalPrice());
        event.setSupplierId(first.getSupplierId());
        event.setApplicantId(first.getApplicantId());
        event.setApplyTime(first.getApplyTime());
        event.setReason(first.getReason());
        event.setRemake(first.getRemake());
        event.setOccurrenceTime(first.getOccurrenceTime());
        event.setStartTime(first.getStartTime());
        event.setEndTime(first.getEndTime());
        event.setPractitionerId(first.getPractitionerId());
        event.setTraceNo(first.getTraceNo());
        domainEventPublisher.publish(event);
    }

    /**
     * 为新建供应单据预分配 {@code bus_no}（不写库；IF-IP-SUM-04 与发放 {@code summary_no} 同源）。
     *
     * @param typeEnumCodeForNewSeq 新建时用于分桶的 {@code SupplyType} 持久化码（{@link SupplyType#getCode()}）
     * @return 按日递增的业务单号
     * @throws BusinessException 未知或空白 type_enum 时
     */
    public String allocateBusNoForType(String typeEnumCodeForNewSeq) {
        return this.generateSupplyBusNo(typeEnumCodeForNewSeq);
    }

    /**
     * 按供应类型分桶生成新单据号（新建路径专用；禁止外部注入）。
     *
     * @param typeEnumCodeForNewSeq 新建时用于分桶的 {@code SupplyType} 持久化码（{@link SupplyType#getCode()}）
     * @return 按日递增的业务单号
     * @throws BusinessException 未知或空白 type_enum 时
     */
    private String generateSupplyBusNo(String typeEnumCodeForNewSeq) {
        String prefix = SupplyType.busNoSeqPrefixForTypeCode(typeEnumCodeForNewSeq);
        return businessSequenceService.getSeqByDay(prefix, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
    }

    /**
     * 由盘点命令项装配一行聚合实体（单端点库房：业务真源仅 {@code purpose_*}，{@code source_*} 不落库）。
     *
     * @param busNo 业务单号
     * @param item  盘点命令行
     * @return 待持久化实体（未保存）
     * @throws BusinessException 盘点库房或类型非法
     */
    private SupplyRequest buildStocktakeEntityFromItem(
        String busNo,
        CreateStocktakeCommand.CreateStocktakeItemCommand item) {

        if (item.getPurposeTypeEnum() == null || item.getPurposeTypeEnum().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "盘点库房类型不能为空");
        }
        if (item.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "盘点库房不能为空");
        }

        SupplyRequest entity = new SupplyRequest();
        entity.setBusNo(busNo);
        entity.setTypeEnum(item.getTypeEnum());
        entity.setItemTable(item.getItemTable());
        entity.setItemId(item.getItemId());
        entity.setItemQuantity(item.getItemQuantity());
        entity.setTotalQuantity(item.getTotalQuantity());
        entity.setUnitCode(item.getUnitCode());
        entity.setPrice(item.getPrice());
        entity.setTotalPrice(item.getTotalPrice());
        entity.setProfitLossPrice(item.getProfitLossPrice());

        if (item.getCategoryEnum() == null) {
            entity.setCategoryEnum(SupplyCategory.STOCK_SUPPLY.getCode());
        } else {
            entity.setCategoryEnum(item.getCategoryEnum());
        }

        String purposeType = item.getPurposeTypeEnum().trim();
        Long purposeLocationId = item.getPurposeLocationId();
        entity.setPurposeTypeEnum(purposeType);
        entity.setPurposeLocationId(purposeLocationId);

        entity.setApplicantId(item.getApplicantId());
        entity.setPractitionerId(item.getPractitionerId());
        entity.setApplyTime(item.getApplyTime());
        entity.setLotNumber(item.getLotNumber());
        entity.setTraceNo(item.getTraceNo());
        entity.setOccurrenceTime(item.getOccurrenceTime());
        entity.setReason(item.getReason());
        entity.setRemake(item.getRemake());
        entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        return entity;
    }

    /**
     * 由科室领用命令行组装供应请求实体（单端点 purpose；禁止 original_bus_no）。
     *
     * @param busNo 业务单号
     * @param item  领用命令行
     * @return 待持久化实体（未保存）
     * @throws BusinessException 科室药房或类型非法
     */
    private SupplyRequest buildDeptIssueEntityFromItem(
        String busNo,
        CreateDeptIssueCommand.CreateDeptIssueItemCommand item) {

        if (item.getPurposeTypeEnum() == null || item.getPurposeTypeEnum().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "科室药房类型不能为空");
        }
        if (item.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "科室药房不能为空");
        }

        SupplyRequest entity = new SupplyRequest();
        entity.setBusNo(busNo);
        entity.setTypeEnum(item.getTypeEnum());
        entity.setItemTable(item.getItemTable());
        entity.setItemId(item.getItemId());
        entity.setItemQuantity(item.getItemQuantity());
        entity.setUnitCode(item.getUnitCode());
        entity.setPrice(item.getPrice());
        entity.setTotalPrice(item.getTotalPrice());

        if (item.getCategoryEnum() == null) {
            entity.setCategoryEnum(SupplyCategory.STOCK_SUPPLY.getCode());
        } else {
            entity.setCategoryEnum(item.getCategoryEnum());
        }

        entity.setPurposeTypeEnum(item.getPurposeTypeEnum().trim());
        entity.setPurposeLocationId(item.getPurposeLocationId());
        // 领用科室：与药房解耦，须由应用层传入非空 Organization 主键
        if (item.getConsumingOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "领用科室不能为空");
        }
        entity.setConsumingOrganizationId(item.getConsumingOrganizationId());
        entity.setApplicantId(item.getApplicantId());
        entity.setPractitionerId(item.getPractitionerId());
        entity.setApplyTime(item.getApplyTime());
        entity.setLotNumber(item.getLotNumber());
        entity.setTraceNo(item.getTraceNo());
        entity.setOccurrenceTime(item.getOccurrenceTime());
        entity.setReason(item.getReason());
        entity.setRemake(item.getRemake());
        // 批号效期：与请领同构落 end_time，供领用明细「有效期」回显
        entity.setEndTime(item.getEndTime());
        entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        return entity;
    }

    /**
     * 由科室退领命令行组装供应请求实体（单端点 purpose；{@code original_bus_no} 必填）。
     *
     * @param busNo 业务单号
     * @param item  退领命令行
     * @return 待持久化实体（未保存）
     * @throws BusinessException 科室药房、源单号或类型非法
     */
    private SupplyRequest buildDeptIssueReturnEntityFromItem(
        String busNo,
        CreateDeptIssueReturnCommand.CreateDeptIssueReturnItemCommand item) {

        if (item.getPurposeTypeEnum() == null || item.getPurposeTypeEnum().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "科室药房类型不能为空");
        }
        if (item.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "科室药房不能为空");
        }
        if (item.getOriginalBusNo() == null || item.getOriginalBusNo().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "科室退领单 originalBusNo 必填");
        }

        SupplyRequest entity = new SupplyRequest();
        entity.setBusNo(busNo);
        entity.setOriginalBusNo(item.getOriginalBusNo().trim());
        entity.setTypeEnum(item.getTypeEnum());
        entity.setItemTable(item.getItemTable());
        entity.setItemId(item.getItemId());
        entity.setItemQuantity(item.getItemQuantity());
        entity.setUnitCode(item.getUnitCode());
        entity.setPrice(item.getPrice());
        entity.setTotalPrice(item.getTotalPrice());

        if (item.getCategoryEnum() == null) {
            entity.setCategoryEnum(SupplyCategory.STOCK_SUPPLY.getCode());
        } else {
            entity.setCategoryEnum(item.getCategoryEnum());
        }

        entity.setPurposeTypeEnum(item.getPurposeTypeEnum().trim());
        entity.setPurposeLocationId(item.getPurposeLocationId());
        // 领用科室：须自源领用单带出，与药房解耦
        if (item.getConsumingOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "退领领用科室不能为空");
        }
        entity.setConsumingOrganizationId(item.getConsumingOrganizationId());
        entity.setApplicantId(item.getApplicantId());
        entity.setPractitionerId(item.getPractitionerId());
        entity.setApplyTime(item.getApplyTime());
        entity.setLotNumber(item.getLotNumber());
        entity.setTraceNo(item.getTraceNo());
        entity.setOccurrenceTime(item.getOccurrenceTime());
        entity.setReason(item.getReason());
        entity.setRemake(item.getRemake());
        // 批号效期：自源领用或前端带回落 end_time，供退领明细「有效期」回显
        entity.setEndTime(item.getEndTime());
        entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        return entity;
    }

    /**
     * 由期初命令行组装供应请求实体（单端点 purpose；效期写 {@code end_time}）。
     *
     * @param busNo      业务单号
     * @param item       期初命令行
     * @param operatorId 操作人（行上 applicant 缺省时回落）
     * @return 待持久化实体（未保存）
     */
    private SupplyRequest buildOpeningEntityFromItem(
        String busNo,
        CreateOpeningCommand.CreateOpeningItemCommand item,
        Long operatorId) {

        if (item.getPurposeTypeEnum() == null || item.getPurposeTypeEnum().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "期初库房类型不能为空");
        }
        if (item.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "期初库房不能为空");
        }

        SupplyRequest entity = new SupplyRequest();
        entity.setBusNo(busNo);
        entity.setTypeEnum(item.getTypeEnum());
        entity.setItemTable(item.getItemTable());
        entity.setItemId(item.getItemId());
        entity.setItemQuantity(item.getItemQuantity());
        entity.setUnitCode(item.getUnitCode());
        entity.setPrice(item.getPrice());
        entity.setTotalPrice(item.getTotalPrice());
        // OPENING 裁决 3C：售价借用 profit_loss_price 持久化（损益单语义不占用本类型）
        entity.setProfitLossPrice(item.getSalePrice());
        entity.setSupplierId(item.getSupplierId());
        entity.setStartTime(item.getStartTime());
        entity.setEndTime(item.getEndTime());

        if (item.getCategoryEnum() == null) {
            entity.setCategoryEnum(SupplyCategory.STOCK_SUPPLY.getCode());
        } else {
            entity.setCategoryEnum(item.getCategoryEnum());
        }

        entity.setPurposeTypeEnum(item.getPurposeTypeEnum().trim());
        entity.setPurposeLocationId(item.getPurposeLocationId());

        Long applicantId = item.getApplicantId();
        if (applicantId == null) {
            applicantId = operatorId;
        }
        if (applicantId == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "applicantId 不能为空");
        }
        entity.setApplicantId(applicantId);
        entity.setPractitionerId(item.getPractitionerId());
        entity.setApplyTime(item.getApplyTime());
        entity.setLotNumber(item.getLotNumber());
        entity.setTraceNo(item.getTraceNo());
        entity.setOccurrenceTime(item.getOccurrenceTime());
        entity.setReason(item.getReason());
        entity.setRemake(item.getRemake());
        entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        return entity;
    }

    /**
     * 由损益命令行组装待持久化实体（新建与修订共用）。
     *
     * @param busNo 业务单号
     * @param item  损益命令行
     * @return 待持久化实体（未保存）
     * @throws BusinessException 损益库房或类型非法
     */
    private SupplyRequest buildGainLossEntityFromItem(
        String busNo,
        CreateGainLossCommand.CreateGainLossItemCommand item) {

        if (item.getPurposeTypeEnum() == null || item.getPurposeTypeEnum().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "损益库房类型不能为空");
        }
        if (item.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "损益库房不能为空");
        }

        SupplyRequest entity = new SupplyRequest();
        entity.setBusNo(busNo);
        entity.setTypeEnum(item.getTypeEnum());
        entity.setItemTable(item.getItemTable());
        entity.setItemId(item.getItemId());
        entity.setItemQuantity(item.getItemQuantity());
        entity.setTotalQuantity(item.getTotalQuantity());
        entity.setUnitCode(item.getUnitCode());
        entity.setPrice(item.getPrice());
        entity.setTotalPrice(item.getTotalPrice());
        entity.setProfitLossPrice(item.getProfitLossPrice());

        if (item.getCategoryEnum() == null) {
            entity.setCategoryEnum(SupplyCategory.STOCK_SUPPLY.getCode());
        } else {
            entity.setCategoryEnum(item.getCategoryEnum());
        }

        String purposeType = item.getPurposeTypeEnum().trim();
        Long purposeLocationId = item.getPurposeLocationId();
        entity.setPurposeTypeEnum(purposeType);
        entity.setPurposeLocationId(purposeLocationId);

        entity.setApplicantId(item.getApplicantId());
        entity.setPractitionerId(item.getPractitionerId());
        entity.setApplyTime(item.getApplyTime());
        entity.setLotNumber(item.getLotNumber());
        entity.setTraceNo(item.getTraceNo());
        entity.setOccurrenceTime(item.getOccurrenceTime());
        entity.setReason(item.getReason());
        entity.setRemake(item.getRemake());
        entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        return entity;
    }

    /**
     * 构建通用字段集合。
     *
     * @throws BusinessException 必填字段缺失
     */
    private SupplyRequest buildCommonEntity(
        String busNo,
        String typeEnum,
        String itemTable,
        Long itemId,
        java.math.BigDecimal itemQuantity,
        String unitCode,
        String categoryEnum,
        String sourceTypeEnum,
        Long sourceLocationId,
        String purposeTypeEnum,
        Long purposeLocationId,
        Long applicantId,
        LocalDateTime applyTime) {

        SupplyRequest entity = new SupplyRequest();
        entity.setBusNo(busNo);
        entity.setTypeEnum(typeEnum);
        entity.setItemTable(itemTable);
        entity.setItemId(itemId);
        entity.setItemQuantity(itemQuantity);
        entity.setUnitCode(unitCode);

        // 必填入参缺失时拒绝继续，避免写脏数据
        if (categoryEnum == null) {
            entity.setCategoryEnum(SupplyCategory.STOCK_SUPPLY.getCode());
        } else {
            entity.setCategoryEnum(categoryEnum);
        }

        if (SupplyType.isPurposeOnlyLocationTypeCode(typeEnum)) {
            // 单库房就是目的：仅写 purpose，source 保持空。
            if (purposeTypeEnum == null || purposeTypeEnum.trim().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "purposeTypeEnum 不能为空");
            }
            if (purposeLocationId == null) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "purposeLocationId 不能为空");
            }
            entity.setPurposeTypeEnum(purposeTypeEnum.trim());
            entity.setPurposeLocationId(purposeLocationId);
        } else {
            // 双端：出为源、进为目的；source 与 purpose 均必填且禁止同值。
            if (sourceTypeEnum == null || sourceTypeEnum.trim().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "sourceTypeEnum 不能为空");
            }
            if (sourceLocationId == null) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "sourceLocationId 不能为空");
            }
            if (purposeTypeEnum == null || purposeTypeEnum.trim().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "purposeTypeEnum 不能为空");
            }
            if (purposeLocationId == null) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "purposeLocationId 不能为空");
            }
            entity.setSourceTypeEnum(sourceTypeEnum.trim());
            entity.setSourceLocationId(sourceLocationId);
            entity.setPurposeTypeEnum(purposeTypeEnum.trim());
            entity.setPurposeLocationId(purposeLocationId);
        }

        entity.setApplicantId(applicantId);
        // 草稿建单阶段不写申请时刻（null）；提交审批时由 submitApproval 写入当前时间。
        entity.setApplyTime(applyTime);
        return entity;
    }


    /**
     * 同号修订：对当前未删行做逻辑删除并立即 flush，再允许同 busNo 重插新行。
     *
     * <p>须走 {@code deleteAll} 触发 SoftDeleteAspect（写 is_deleted/deleted_at），且 flush 后再 insert，
     * 避免同事务内读到未落库的软删状态或「旧行+新行」双份。</p>
     *
     * @param existingLines {@link #findByBusNoAndIsDeletedFalse} 查出的待替换行
     */
    private void softDeleteExistingLinesForRevision(List<SupplyRequest> existingLines) {
        List<SupplyRequest> toDelete = new ArrayList<>();
        for (SupplyRequest existingLine : existingLines) {
            // 空元素跳过，不参与软删批次
            if (existingLine == null) {
                continue;
            }
            toDelete.add(existingLine);
        }
        // 无有效行则无需删，直接由调用方重插
        if (toDelete.isEmpty()) {
            return;
        }
        supplyRequestRepository.deleteAll(toDelete);
        supplyRequestRepository.flush();
    }
    /**
     * 将当前持久化上下文中已排队的供应请求变更强制刷入数据库（同意/发放等编排后读回前调用）。
     */
    public void flushPendingChanges() {
        supplyRequestRepository.flush();
    }
}