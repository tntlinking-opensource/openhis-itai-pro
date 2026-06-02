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

package com.openhis.health.domain.workflow.supplyrequest.service;

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
import com.openhis.health.domain.workflow.supplyrequest.command.CreateDeviceSummarySupplyRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateGainLossCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateInboundReceiptCommand;
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
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.entity.SupplyRequest;
import com.openhis.health.domain.workflow.supplyrequest.repository.SupplyRequestRepository;
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
 * <p>命令与实体中凡 {@code *_enum} 语义字段均与库表 VARCHAR 对齐，使用各业务枚举 {@code getCode()} 持久化字符串（B.10.8.1）；
 * 本服务内状态迁移通过 {@link SupplyStatus#getCode()} 等常量赋值，禁止写死无意义数字串。</p>
 */
@Service
@RequiredArgsConstructor
public class SupplyRequestDomainService {

    private final SupplyRequestRepository supplyRequestRepository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 供应请求 busNo：外部未传时按日流水分配，序号键由 {@link SupplyType#busNoSeqPrefixForTypeCode(String)} 按 {@code type_enum} 分桶。 */
    private final BusinessSequenceService businessSequenceService;


    /**
     * 创建或修订药库订货单（同一聚合入口：{@link SupplyType#CABINET_PURCHASE_ORDER} 多行明细共享同一 {@code bus_no}）。
     *
     * <p><b>新建路径</b>：命令首行 {@link CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand#getBusNo()} 为空或空白时，
     * 由 {@link #ensureOrGenerateBusNo(String, String)} 按业务日生成新 {@code busNo}（序号键见 {@link SupplyType#busNoSeqPrefixForTypeCode(String)}，
     * 序号策略见 {@link BusinessSequenceService}），再批量插入多行，状态统一为待审核；插入成功后发布
     * {@link SupplyRequestCreatedEvent}（须在持久化完成之后，见全局规范「先 save 后 publish」）。</p>
     *
     * {@code busNo} 时表示「同一张订货单修改明细」而非新开单。语义为<strong>全量明细对齐</strong>：先将该 {@code busNo}
     * 下当前未删除的订货行全部标记逻辑删除并一次性落库，再按命令中的明细列表<strong>整批重插</strong>为新行（仍为同一 {@code busNo}）。
     * 门控由 {@link SupplyRequest#assertPurchaseOrderLinesRevisable(List)} 承担：须为药库订货类型且各行均为待审核态，
     * 与 BR-SR-003 / 应用层 {@code verifyAbleEdit} 前置一致；若 {@code busNo} 在库中不存在则断言抛出「无法修订」。</p>
     *
     * <p><b>修订路径为何不发布 {@link SupplyRequestCreatedEvent}</b>（DOC-CONFLICT）：DM §5 当前清单以 Created 为主，
     * 未见「订货明细已替换」专用事件；若此处再次发布 Created，下游投影/集成可能将「编辑保存」误判为「又来一张新单」。
     * 若产品后续定义修订事件，应在此处补充 publish，并同步修订 DM 与消费方。</p>
     *
     * <p><b>与进货单边界</b>：本方法<strong>不</strong>创建进货单（{@link #createInboundReceipt(CreateInboundReceiptCommand)}），
     * 订货同意后的进货编排见其它应用服务（AS WHS-SDD-R-001）。</p>
     *
     * @param command 订货命令；须已通过 {@link CreatePurchaseOrderCommand#resolveItems()} 可用的批量语义，
     *                内含 {@code operatorId} 用于新建路径事件载荷；每行 item 须携带位置、品类、数量等业务字段
     * @return 最终生效的业务单号 {@code busNo}（新建为新分配号码，修订为入参既有号码）
     * @throws BusinessException 命令解析失败、修订时单号不存在、类型或状态不允许修订、或子实体校验失败（含 {@link SupplyRequest#assertCreatableMinimumFields()}）
     */
    public String createPurchaseOrder(CreatePurchaseOrderCommand command) {
        // 双形态命令统一解析为列表；领域层要求至少一行，空列表由 resolveItems 或后续断言阻断。
        List<CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand> items = command.resolveItems();
        CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand head = items.get(0);
        String requestedBusNo = head.getBusNo();
        // 与 AS 约定一致：是否修订只看「首行是否显式携带业务单号」，新建场景调用方不应填 busNo。
        boolean revisionRequested = requestedBusNo != null && !requestedBusNo.trim().isEmpty();

        if (revisionRequested) {
            String busNo = requestedBusNo.trim();
            // 修订：按业务单号加载当前可见（未软删）的全部订货行，一次性读取便于批量标记删除。
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNo(busNo);
            // 聚合不变式：须存在、须为药库订货类型、须仍可编辑（待审核）；不满足则业务异常，禁止 silent skip。
            SupplyRequest.assertPurchaseOrderLinesRevisable(existingLines);

            List<SupplyRequest> toMarkDeleted = new ArrayList<>();
            for (SupplyRequest existingLine : existingLines) {
                if (existingLine == null) {
                    continue;
                }
                // 逻辑删除旧明细行，保留历史主键与审计字段由底座/仓储策略写入；不在此处发布删除事件以免与「修订」语义混淆。
                existingLine.setIsDeleted(Boolean.TRUE);
                toMarkDeleted.add(existingLine);
            }
            // 批量持久化软删结果；必须先完成删除侧写入，再插入新行，避免同号并发读到半成品明细。
            supplyRequestRepository.saveAll(toMarkDeleted);

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
        // 首行未带来 busNo：生成当日流水号，保证与「业务发生日」归档口径一致（见通用规范 busNo 按日策略）。
        String typeForBusNo = head.getTypeEnum();
        if (typeForBusNo == null || typeForBusNo.isBlank()) {
            typeForBusNo = SupplyType.CABINET_PURCHASE_ORDER.getCode();
        }
        String busNo = this.ensureOrGenerateBusNo(null, typeForBusNo);

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
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNo(busNo);
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

            List<SupplyRequest> toMarkDeleted = new ArrayList<>();
            for (SupplyRequest existingLine : existingLines) {
                if (existingLine == null) {
                    continue;
                }
                existingLine.setIsDeleted(Boolean.TRUE);
                toMarkDeleted.add(existingLine);
            }
            supplyRequestRepository.saveAll(toMarkDeleted);

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
        String busNo = this.ensureOrGenerateBusNo(null, typeForBusNo);
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
        entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        return entity;
    }

    /**
     * 创建或修订药库退货单（{@link SupplyType#CABINET_RETURN_TO_SUPPLIER}）：首行携带既有 {@code busNo} 时为全量明细替换（无乐观锁版本，由应用层禁止传入 rowVersion）。
     *
     * @param command 退货命令
     * @return 业务单号
     */
    public String createReturnToSupplier(CreateReturnToSupplierCommand command) {
        List<CreateReturnToSupplierCommand.CreateReturnToSupplierItemCommand> items = command.resolveItems();
        CreateReturnToSupplierCommand.CreateReturnToSupplierItemCommand head = items.get(0);
        String requestedBusNo = head.getBusNo();
        boolean revisionRequested = requestedBusNo != null && !requestedBusNo.trim().isEmpty();

        if (revisionRequested) {
            String busNo = requestedBusNo.trim();
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNo(busNo);
            SupplyRequest.assertReturnToSupplierLinesRevisable(existingLines);

            List<SupplyRequest> toMarkDeleted = new ArrayList<>();
            for (SupplyRequest existingLine : existingLines) {
                if (existingLine == null) {
                    continue;
                }
                existingLine.setIsDeleted(Boolean.TRUE);
                toMarkDeleted.add(existingLine);
            }
            supplyRequestRepository.saveAll(toMarkDeleted);

            List<SupplyRequest> toInsert = new ArrayList<>();
            for (CreateReturnToSupplierCommand.CreateReturnToSupplierItemCommand item : items) {
                SupplyRequest entity = this.buildReturnToSupplierEntityFromItem(busNo, item);
                entity.assertCreatableMinimumFields();
                toInsert.add(entity);
            }
            supplyRequestRepository.saveAll(toInsert);
            return busNo;
        }

        String typeForBusNo = head.getTypeEnum();
        if (typeForBusNo == null || typeForBusNo.isBlank()) {
            typeForBusNo = SupplyType.CABINET_RETURN_TO_SUPPLIER.getCode();
        }
        String busNo = this.ensureOrGenerateBusNo(null, typeForBusNo);
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

    private SupplyRequest buildReturnToSupplierEntityFromItem(String busNo, CreateReturnToSupplierCommand.CreateReturnToSupplierItemCommand item) {
        String typeForRow = item.getTypeEnum();
        if (typeForRow == null || typeForRow.trim().isEmpty()) {
            typeForRow = SupplyType.CABINET_RETURN_TO_SUPPLIER.getCode();
        }
        SupplyRequest entity = this.buildCommonEntity(busNo, typeForRow, item.getItemTable(), item.getItemId(),
            item.getItemQuantity(), item.getUnitCode(), item.getCategoryEnum(), item.getSourceTypeEnum(), item.getSourceLocationId(),
            item.getPurposeTypeEnum(), item.getPurposeLocationId(), item.getApplicantId(), item.getApplyTime());
        entity.setOriginalBusNo(item.getOriginalBusNo());
        entity.setSupplierId(item.getSupplierId());
        entity.setOccurrenceTime(item.getOccurrenceTime());
        entity.setReason(item.getReason());
        entity.setRemake(item.getRemake());
        entity.setLotNumber(item.getLotNumber());
        entity.setTraceNo(item.getTraceNo());
        entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        if (entity.getOriginalBusNo() == null || entity.getOriginalBusNo().trim().isEmpty()) {
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
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNo(busNo);
            SupplyRequest.assertCabinetStockOutLinesRevisable(existingLines);
            List<SupplyRequest> toMarkDeleted = new ArrayList<>();
            for (SupplyRequest existingLine : existingLines) {
                if (existingLine == null) {
                    continue;
                }
                existingLine.setIsDeleted(Boolean.TRUE);
                toMarkDeleted.add(existingLine);
            }
            supplyRequestRepository.saveAll(toMarkDeleted);
            List<SupplyRequest> toInsert = new ArrayList<>();
            for (CreateStockOutCommand.CreateStockOutItemCommand item : items) {
                SupplyRequest entity = this.buildStockOutEntityFromItem(busNo, item);
                entity.assertCreatableMinimumFields();
                toInsert.add(entity);
            }
            supplyRequestRepository.saveAll(toInsert);
            return busNo;
        }

        String busNo = this.ensureOrGenerateBusNo(soHead.getBusNo(), typeForBusNo);
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
     * 创建或修订退库单（药房退上游 / 药库下游退回等）：带既有 busNo 且类型可修订时软删旧行整批重插；否则新建并发布创建事件。
     *
     * @param command 退库明细（须含 originalBusNo 等）
     * @return 业务单号
     * @throws BusinessException 类型不支持修订、单不存在或字段非法时
     */
    public String createReturn(CreateReturnCommand command) {
        List<CreateReturnCommand.CreateReturnItemCommand> items = command.resolveItems();
        CreateReturnCommand.CreateReturnItemCommand head = items.get(0);
        String requestedBusNo = head.getBusNo();
        boolean revisionRequested = requestedBusNo != null && !requestedBusNo.trim().isEmpty();

        if (revisionRequested) {
            String busNo = requestedBusNo.trim();
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNo(busNo);
            if (existingLines.isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "退库单不存在，无法修订");
            }
            SupplyRequest headExisting = existingLines.get(0);
            if (headExisting != null && SupplyType.PHARMACY_RETURN_TO_UPSTREAM.getCode().equals(headExisting.getTypeEnum())) {
                SupplyRequest.assertPharmacyReturnUpstreamLinesRevisable(existingLines);
            } else if (headExisting != null && SupplyType.CABINET_RETURN_FROM_DOWNSTREAM.getCode().equals(headExisting.getTypeEnum())) {
                SupplyRequest.assertCabinetReturnFromDownstreamLinesRevisable(existingLines);
            } else {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "该退库单据类型不支持通过 createReturn 修订");
            }

            List<SupplyRequest> toMarkDeleted = new ArrayList<>();
            for (SupplyRequest existingLine : existingLines) {
                if (existingLine == null) {
                    continue;
                }
                existingLine.setIsDeleted(Boolean.TRUE);
                toMarkDeleted.add(existingLine);
            }
            supplyRequestRepository.saveAll(toMarkDeleted);

            List<SupplyRequest> toInsert = new ArrayList<>();
            for (CreateReturnCommand.CreateReturnItemCommand item : items) {
                SupplyRequest entity = this.buildReturnEntityFromItem(busNo, item);
                entity.assertCreatableMinimumFields();
                toInsert.add(entity);
            }
            supplyRequestRepository.saveAll(toInsert);
            return busNo;
        }

        String busNo = this.ensureOrGenerateBusNo(items.get(0).getBusNo(), items.get(0).getTypeEnum());

        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreateReturnCommand.CreateReturnItemCommand item : items) {
            SupplyRequest entity = this.buildReturnEntityFromItem(busNo, item);
            entity.assertCreatableMinimumFields();
            toSave.add(entity);
        }

        List<SupplyRequest> saved = supplyRequestRepository.saveAll(toSave);
        this.publishCreated(saved.get(0), command.getOperatorId(), items.size());
        return busNo;
    }

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
        entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());

        if (entity.getOriginalBusNo() == null || entity.getOriginalBusNo().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "退库单 originalBusNo 必填");
        }
        return entity;
    }

    /**
     * 创建损益执行单多行：审批通过后由应用层驱动发放与库存；初态待审核。
     *
     * @param command 损益明细与操作人
     * @return 业务单号
     * @throws BusinessException 不变式失败时
     */
    public String createGainLoss(CreateGainLossCommand command) {
        List<CreateGainLossCommand.CreateGainLossItemCommand> items = command.resolveItems();
        String busNo = this.ensureOrGenerateBusNo(items.get(0).getBusNo(), items.get(0).getTypeEnum());

        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreateGainLossCommand.CreateGainLossItemCommand item : items) {
            // 业务含义：创建损益执行单（审批同意后触发损溢发放与库存增减，由应用层编排）。
            SupplyRequest entity = this.buildCommonEntity(busNo, item.getTypeEnum(), item.getItemTable(), item.getItemId(),
                item.getItemQuantity(), item.getUnitCode(), item.getCategoryEnum(), item.getSourceTypeEnum(), item.getSourceLocationId(),
                null, null, item.getApplicantId(), item.getApplyTime());
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
        List<CreateStocktakeCommand.CreateStocktakeItemCommand> items = command.resolveItems();
        CreateStocktakeCommand.CreateStocktakeItemCommand head = items.get(0);
        String requestedBusNo = head.getBusNo();
        boolean revisionRequested = requestedBusNo != null && !requestedBusNo.trim().isEmpty();

        if (revisionRequested) {
            String busNo = requestedBusNo.trim();
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNo(busNo);
            SupplyRequest.assertStocktakeLinesRevisable(existingLines);

            List<SupplyRequest> toMarkDeleted = new ArrayList<>();
            for (SupplyRequest existingLine : existingLines) {
                if (existingLine == null) {
                    continue;
                }
                // 修订语义：旧明细行逻辑删除，保留审计轨迹；不在此发布删除事件。
                existingLine.setIsDeleted(Boolean.TRUE);
                toMarkDeleted.add(existingLine);
            }
            supplyRequestRepository.saveAll(toMarkDeleted);

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

        String busNo = this.ensureOrGenerateBusNo(head.getBusNo(), head.getTypeEnum());

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
     * 创建调拨执行单多行：源库位与目的库位须不同；初态待审核，同意后由应用层编排源减目增。
     *
     * @param command 调拨明细与操作人
     * @return 业务单号
     * @throws BusinessException 源目相同或字段非法时
     */
    public String createTransfer(CreateTransferCommand command) {
        List<CreateTransferCommand.CreateTransferItemCommand> items = command.resolveItems();
        String busNo = this.ensureOrGenerateBusNo(items.get(0).getBusNo(), items.get(0).getTypeEnum());

        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreateTransferCommand.CreateTransferItemCommand item : items) {
            // 业务含义：创建调拨执行单（源目不同，审批同意后触发源减目增发放与库存变动，由应用层编排）。
            SupplyRequest entity = this.buildCommonEntity(busNo, item.getTypeEnum(), item.getItemTable(), item.getItemId(),
                item.getItemQuantity(), item.getUnitCode(), item.getCategoryEnum(), item.getSourceTypeEnum(), item.getSourceLocationId(),
                item.getPurposeTypeEnum(), item.getPurposeLocationId(), item.getApplicantId(), item.getApplyTime());
            entity.setOriginalBusNo(item.getOriginalBusNo());
            entity.setOccurrenceTime(item.getOccurrenceTime());
            entity.setReason(item.getReason());
            entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());

            // 业务分支处理。
            if (entity.getSourceLocationId() != null && entity.getPurposeLocationId() != null) {
                // 业务分支处理。
                if (entity.getSourceLocationId().equals(entity.getPurposeLocationId())) {
                    throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "调拨源位置与目标位置不能相同");
                }
            }
            entity.assertCreatableMinimumFields();
            toSave.add(entity);
        }

        List<SupplyRequest> saved = supplyRequestRepository.saveAll(toSave);
        this.publishCreated(saved.get(0), command.getOperatorId(), items.size());
        return busNo;
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
        List<CreateRequisitionCommand.CreateRequisitionItemCommand> items = command.resolveItems();
        CreateRequisitionCommand.CreateRequisitionItemCommand head = items.get(0);
        String requestedBusNo = head.getBusNo();
        boolean revisionRequested = requestedBusNo != null && !requestedBusNo.trim().isEmpty();

        if (revisionRequested) {
            String busNo = requestedBusNo.trim();
            List<SupplyRequest> existingLines = supplyRequestRepository.findByBusNo(busNo);
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

            List<SupplyRequest> toMarkDeleted = new ArrayList<>();
            for (SupplyRequest existingLine : existingLines) {
                if (existingLine == null) {
                    continue;
                }
                existingLine.setIsDeleted(Boolean.TRUE);
                toMarkDeleted.add(existingLine);
            }
            supplyRequestRepository.saveAll(toMarkDeleted);

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

        String busNo = this.ensureOrGenerateBusNo(items.get(0).getBusNo(), items.get(0).getTypeEnum());

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
        entity.setReason(item.getReason());
        entity.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());

        // 手工药房入库（PHARMACY_STOCK_IN）允许 originalBusNo 为空；科室入库（DEPT_STOCK_IN）仍须绑定上游执行单号（AS-INV-PHA-001 / DM 口径）。
        if (SupplyType.DEPT_STOCK_IN.getCode().equals(entity.getTypeEnum())) {
            if (entity.getOriginalBusNo() == null || entity.getOriginalBusNo().trim().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "入库执行单 originalBusNo 必填");
            }
        }
        return entity;
    }

    /**
     * 创建药品汇总单明细多行：关联 summaryId、患者等，初态一般为 {@link SupplyStatus#SEND}。
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
        String busNo = this.ensureOrGenerateBusNo(msHead.getBusNo(), typeForBusNo);

        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreateSummarySupplyRequestCommand.CreateSummarySupplyRequestItemCommand item : items) {
            // 业务含义：创建药品汇总单明细行（典型初态 SEND）。
            SupplyRequest entity = this.buildCommonEntity(busNo, item.getTypeEnum(), item.getItemTable(), item.getItemId(),
                item.getItemQuantity(), item.getUnitCode(), item.getCategoryEnum(), item.getSourceTypeEnum(), item.getSourceLocationId(),
                null, null, item.getApplicantId(), item.getApplyTime());
            entity.setSummaryId(item.getSummaryId());
            entity.setPatientId(item.getPatientId());
            entity.setLotNumber(item.getLotNumber());
            entity.setOccurrenceTime(item.getOccurrenceTime());
            entity.setReason(item.getReason());
            entity.setStatusEnum(SupplyStatus.SEND.getCode());

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
        String busNo = this.ensureOrGenerateBusNo(ddHead.getBusNo(), typeForBusNo);

        List<SupplyRequest> toSave = new ArrayList<>();
        for (CreateDeviceSummarySupplyRequestCommand.CreateDeviceSummarySupplyRequestItemCommand item : items) {
            // 业务含义：创建科室耗材盘点单明细行（type 28）。
            SupplyRequest entity = this.buildCommonEntity(busNo, item.getTypeEnum(), item.getItemTable(), item.getItemId(),
                item.getItemQuantity(), item.getUnitCode(), item.getCategoryEnum(), item.getSourceTypeEnum(), item.getSourceLocationId(),
                null, null, item.getApplicantId(), item.getApplyTime());
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
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "提交审批一次仅允许提交一张单据");
        }
        SubmitApprovalCommand.SubmitApprovalItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getBusNo() == null || item.getBusNo().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "busNo 不能为空");
        }

        // 业务含义：同 busNo 下所有明细行批量推进到 APPROVAL，并写入申请时刻（服务端当前时间）与申请人（会话医务人员）。
        List<SupplyRequest> entities = supplyRequestRepository.findByBusNo(item.getBusNo().trim());
        // 业务分支处理。
        if (entities.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "单据不存在");
        }

        Long applicantId = item.getApplicantId();
        // 业务分支处理。
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
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "撤回审批一次仅允许提交一张单据");
        }
        WithdrawApprovalCommand.WithdrawApprovalItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getBusNo() == null || item.getBusNo().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "busNo 不能为空");
        }

        // 业务含义：同 busNo 下所有明细行批量推进到 WITHDRAW。
        List<SupplyRequest> entities = supplyRequestRepository.findByBusNo(item.getBusNo().trim());
        // 业务分支处理。
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
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "审批同意一次仅允许提交一张单据");
        }
        AgreeRequestCommand.AgreeRequestItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getBusNo() == null || item.getBusNo().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "busNo 不能为空");
        }
        // 业务分支处理。
        if (item.getApproverId() == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "approverId 不能为空");
        }

        // 防重：任一行已 AGREE 则拒绝（幂等门控由读侧 supplyRequestValidation 承接）。
        boolean hasAgreed = supplyRequestRepository.existsByBusNoAndStatusEnum(item.getBusNo().trim(), SupplyStatus.AGREE.getCode());
        // 业务分支处理。
        if (hasAgreed) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "单据已同意，禁止重复同意");
        }

        List<SupplyRequest> entities = supplyRequestRepository.findByBusNo(item.getBusNo().trim());
        // 业务分支处理。
        if (entities.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "单据不存在");
        }

        LocalDateTime approvalTime = LocalDateTime.now();
        for (SupplyRequest entity : entities) {
            entity.assertApprovable();
            entity.approvalToAgree(item.getApproverId(), approvalTime);
        }

        List<SupplyRequest> saved = supplyRequestRepository.saveAll(entities);
        SupplyRequest first = saved.get(0);
        SupplyRequestAgreedEvent agreedEvent = new SupplyRequestAgreedEvent(first.getId(), first.getBusNo(), first.getTypeEnum(), first.getApproverId(),
            first.getApprovalTime(), command.getOperatorId());
        agreedEvent.setStatusEnum(first.getStatusEnum());
        agreedEvent.setTenantId(first.getTenantId());
        domainEventPublisher.publish(agreedEvent);
        return true;
    }

    /**
     * 审批驳回：同一 {@code bus_no} 全行批量进入驳回态，并发布 {@link SupplyRequestRejectedEvent}（载荷含变更原因码）。
     *
     * @param command 批次内须恰好一项；{@code changeReasonCode} 可由应用层可选传入（与 AS-SDD §3.10 一致，可空）
     * @return 成功时固定 {@code true}
     * @throws BusinessException {@link ErrorCodeConstants#SUPPLY_REQUEST_INVALID_ARGUMENT}；{@link ErrorCodeConstants#SUPPLY_REQUEST_NOT_FOUND}；
     *                            {@link SupplyRequest#assertRejectable()} 失败
     */
    public boolean rejectRequest(RejectRequestCommand command) {
        List<RejectRequestCommand.RejectRequestItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "审批驳回一次仅允许提交一张单据");
        }
        RejectRequestCommand.RejectRequestItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getBusNo() == null || item.getBusNo().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "busNo 不能为空");
        }
        // 业务分支处理。
        if (item.getApproverId() == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "approverId 不能为空");
        }

        List<SupplyRequest> entities = supplyRequestRepository.findByBusNo(item.getBusNo().trim());
        // 业务分支处理。
        if (entities.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "单据不存在");
        }

        LocalDateTime approvalTime = LocalDateTime.now();
        for (SupplyRequest entity : entities) {
            entity.assertRejectable();
            entity.approvalToReject(item.getApproverId(), approvalTime);
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
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "删除单据一次仅允许提交一张单据");
        }
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getBusNo() == null || item.getBusNo().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "busNo 不能为空");
        }

        // DOC-CONFLICT: BR-SR-010 要求“禁止删除已产生下游发放/库存的单据”，该门控需要跨域校验（SupplyDelivery/InventoryItem）。
        // 领域层无法读取其他聚合数据；应用层应在调用前完成跨域校验。
        List<SupplyRequest> entities = supplyRequestRepository.findByBusNo(item.getBusNo().trim());
        // 业务分支处理。
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
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "取消汇总单一次仅允许提交一组汇总编号");
        }
        CancelSummarySupplyRequestCommand.CancelSummarySupplyRequestItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getSummaryIdList() == null || item.getSummaryIdList().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "summaryIdList 不能为空");
        }

        // 业务含义：仅取消 type 27 的汇总单行（此处按 summaryIdList 批量逻辑删除）。
        List<SupplyRequest> entities = supplyRequestRepository.findBySummaryIdIn(item.getSummaryIdList());
        List<Long> affectedIdList = new ArrayList<>();
        for (SupplyRequest entity : entities) {
            // 业务分支处理。
            if (entity == null) {
                continue;
            }
            // 业务分支处理。
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
     * 生成或归一化单据号。
     *
     * @param busNo 外部传入单据号
     * @param typeEnumCodeForNewSeq 新建时用于分桶的 {@code SupplyType} 持久化码（{@link SupplyType#getCode()}）；空白或无法识别时回退短键 {@code SR}（见 {@link SupplyType#busNoSeqPrefixForTypeCode(String)}）
     * @return 归一化后的单据号
     */
    private String ensureOrGenerateBusNo(String busNo, String typeEnumCodeForNewSeq) {
        // 业务分支处理。
        if (busNo != null && !busNo.trim().isEmpty()) {
            return busNo.trim();
        }
        String prefix = SupplyType.busNoSeqPrefixForTypeCode(typeEnumCodeForNewSeq);
        return businessSequenceService.getSeqByDay(prefix, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
    }

    /**
     * 由盘点命令项装配一行聚合实体（单端点库房：业务真源在 {@code purpose_*}，{@code source_*} 与 purpose 镜像以满足库表非空）。
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
        // 单端点盘点：对外契约以 purpose 为真源；source 与 purpose 同值镜像，满足 wkf_supply_request 非空列。
        entity.setSourceTypeEnum(purposeType);
        entity.setSourceLocationId(purposeLocationId);

        entity.setApplicantId(item.getApplicantId());
        entity.setPractitionerId(item.getPractitionerId());
        entity.setApplyTime(item.getApplyTime());
        entity.setLotNumber(item.getLotNumber());
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

        // 业务分支处理。
        if (categoryEnum == null) {
            entity.setCategoryEnum(SupplyCategory.STOCK_SUPPLY.getCode());
        } else {
            entity.setCategoryEnum(categoryEnum);
        }

        boolean cabinetPurchaseOrder = SupplyType.CABINET_PURCHASE_ORDER.getCode().equals(typeEnum);
        if (cabinetPurchaseOrder) {
            if (purposeTypeEnum == null) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "purposeTypeEnum 不能为空");
            }
            if (purposeLocationId == null) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "purposeLocationId 不能为空");
            }
            entity.setSourceTypeEnum(sourceTypeEnum);
            entity.setSourceLocationId(sourceLocationId);
            entity.setPurposeTypeEnum(purposeTypeEnum);
            entity.setPurposeLocationId(purposeLocationId);
        } else {
            if (sourceTypeEnum == null) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "sourceTypeEnum 不能为空");
            }
            if (sourceLocationId == null) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "sourceLocationId 不能为空");
            }
            if (purposeTypeEnum == null) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "purposeTypeEnum 不能为空");
            }
            if (purposeLocationId == null) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "purposeLocationId 不能为空");
            }
            entity.setSourceTypeEnum(sourceTypeEnum);
            entity.setSourceLocationId(sourceLocationId);
            entity.setPurposeTypeEnum(purposeTypeEnum);
            entity.setPurposeLocationId(purposeLocationId);
        }

        entity.setApplicantId(applicantId);
        // 业务分支处理：草稿建单阶段不写申请时刻（null）；提交审批时由 submitApproval 写入当前时间。
        entity.setApplyTime(applyTime);
        return entity;
    }

    /**
     * 将当前持久化上下文中已排队的供应请求变更强制刷入数据库（同意/发放等编排后读回前调用）。
     */
    public void flushPendingChanges() {
        supplyRequestRepository.flush();
    }
}
