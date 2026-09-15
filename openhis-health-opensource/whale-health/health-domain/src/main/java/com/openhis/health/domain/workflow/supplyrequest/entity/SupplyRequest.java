/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplyrequest.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 供应请求聚合根（同表多行：同一 {@code bus_no} 聚合多行明细，共享审批状态）。
 *
 * <p>边界：本聚合只维护单据行与审批流转；发放与库存变动由应用层编排（SupplyDelivery / InventoryItem）承接。</p>
 *
 * <p>凡 {@code type_enum}/{@code status_enum}/{@code category_enum} 等与领域枚举对应的列，持久化值均为各枚举
 * {@link com.openhis.health.domain.share.enums.SupplyType#getCode()}、{@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()} 等
 * 字符串（B.10.8.1）；实体方法内比对禁止使用排序号整型。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "wkf_supply_request")
public class SupplyRequest extends BaseAuditedEntity {

    /** 业务单号；同号多行共享审批生命周期。 */
    @Column(name = "bus_no", nullable = false)
    private String busNo;

    /** 来源业务单号（如进货关联订货）；可空。 */
    @Column(name = "original_bus_no")
    private String originalBusNo;

    /** 供应类型持久化码（{@link SupplyType#getCode()}，B.10.8.1）。 */
    @Column(name = "type_enum", nullable = false)
    private String typeEnum;

    /** 供应状态持久化码（{@link SupplyStatus#getCode()}，B.10.8.1）。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    @Column(name = "intent_enum")
    private String intentEnum;

    /** 供应类别持久化码（与 {@link com.openhis.health.domain.share.enums.SupplyCategory#getCode()} 对齐，B.10.8.1）。 */
    @Column(name = "category_enum", nullable = false)
    private String categoryEnum;

    @Column(name = "priority_enum")
    private String priorityEnum;

    @Column(name = "summary_id")
    private Long summaryId;

    @Column(name = "deliver_id")
    private Long deliverId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "item_table", nullable = false)
    private String itemTable;

    /**
     * 行数量：入出库等为执行数量（拆零、正数）；盘点/损益等为盈亏数量（拆零，可正可负可零）。
     */
    @Column(name = "item_quantity", nullable = false)
    private BigDecimal itemQuantity;

    /**
     * 合计/实盘数量：盘点场景存实盘数量（拆零、≥0）；其它单据按业务口径使用。
     */
    @Column(name = "total_quantity")
    private BigDecimal totalQuantity;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "unit_code", nullable = false)
    private String unitCode;

    @Column(name = "occurrence_time")
    private LocalDateTime occurrenceTime;

    /** 效期区间起点（与 {@code end_time} 成对；可空表示未维护）。 */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** 效期区间止点（入库入账时可用于库存项有效期等编排）。 */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "practitioner_id")
    private Long practitionerId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "reason")
    private String reason;

    /** 单库房单据为空；双端点单据必填（出方）。 */
    @Column(name = "source_type_enum")
    private String sourceTypeEnum;

    /** 单库房单据为空；双端点单据必填（出方）。 */
    @Column(name = "source_location_id")
    private Long sourceLocationId;

    /** 单库房单据必填；双端点单据必填。 */
    @Column(name = "purpose_type_enum")
    private String purposeTypeEnum;

    /** 单库房就是目的；双端点为进方。 */
    @Column(name = "purpose_location_id")
    private Long purposeLocationId;

    /**
     * 领用科室（行政 {@code Organization.id}）。
     *
     * <p>{@code DEPT_ISSUE} 由客户端选择并落库；{@code DEPT_ISSUE_RETURN} 自源领用单带出；
     * 历史单可空，读侧可回落 {@code Location.organization_id}。</p>
     */
    @Column(name = "consuming_organization_id")
    private Long consumingOrganizationId;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "approval_time")
    private LocalDateTime approvalTime;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    /** 提交审批时的申请时刻；草稿阶段可为空，提交时由服务端写入当前时间。 */
    @Column(name = "apply_time")
    private LocalDateTime applyTime;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "trace_no", length = 50000)
    private String traceNo;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "total_price")
    private BigDecimal totalPrice;

    /** 盘点盈亏金额：盘盈为正、盘亏为负；非盘点类型可空。 */
    @Column(name = "profit_loss_price")
    private BigDecimal profitLossPrice;

    @Column(name = "remake")
    private String remake;

    /**
     * 采购发票号（同号多行共享；进销存 3503/3504 {@code purc_invo_no} 真源）。
     * <p>业务未填时库默认 {@code 0}；上报侧空值亦回落该占位，不拦进退货。</p>
     */
    @Column(name = "invoice_no")
    private String invoiceNo;

    /** 乐观锁版本（与表 {@code version} 列对齐）。 */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * 最小建单断言（BR-SR-001）。
     *
     * @throws BusinessException 参数非法
     */
    public void assertCreatableMinimumFields() {
        if (this.busNo == null || this.busNo.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "busNo 不能为空");
        }
        if (this.typeEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "typeEnum 不能为空");
        }
        if (this.itemTable == null || this.itemTable.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "itemTable 不能为空");
        }
        if (this.itemId == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "itemId 不能为空");
        }
        if (SupplyType.isStocktakeTypeCode(this.typeEnum)) {
            if (this.itemQuantity == null) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "盘点盈亏数量不能为空");
            }
            if (this.totalQuantity != null && this.totalQuantity.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "盘点实盘数量不能为负");
            }
            if (this.purposeTypeEnum == null || this.purposeTypeEnum.trim().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "盘点库房类型不能为空");
            }
            if (this.purposeLocationId == null) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "盘点库房不能为空");
            }
        } else if (SupplyType.isGainLossTypeCode(this.typeEnum)) {
            // 损益：正数报溢、负数报损；禁止空与 0（与盘点「可零」口径区分）。
            if (this.itemQuantity == null || this.itemQuantity.compareTo(BigDecimal.ZERO) == 0) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "损益数量不能为空或为 0");
            }
            if (this.purposeTypeEnum == null || this.purposeTypeEnum.trim().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "损益库房类型不能为空");
            }
            if (this.purposeLocationId == null) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "损益库房不能为空");
            }
        } else if (SupplyType.isPurposeOnlyLocationTypeCode(this.typeEnum)) {
            if (this.purposeTypeEnum == null || this.purposeTypeEnum.trim().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "库房类型不能为空");
            }
            if (this.purposeLocationId == null) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "库房不能为空");
            }
            if (this.itemQuantity == null || this.itemQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "itemQuantity 必须大于 0");
            }
        } else if (this.itemQuantity == null || this.itemQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "itemQuantity 必须大于 0");
        }
        if (this.unitCode == null || this.unitCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "unitCode 不能为空");
        }

        // 白名单校验：typeEnum 必须在 SupplyType 枚举白名单内。
        boolean allowed = false;
        for (SupplyType supplyType : SupplyType.values()) {
            if (supplyType.getCode().equals(this.typeEnum)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "typeEnum 不在白名单范围内: " + this.typeEnum);
        }
    }

    /**
     * 提交审批状态断言（BR-SR-002/BR-SR-013）。
     *
     * @throws BusinessException 状态不允许
     */
    public void assertSubmittable() {
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "statusEnum 不能为空");
        }
        // 业务含义：待审核、已撤回、驳回均可再次提交；驳回后改单重提，否则驳回无业务闭环。
        if (SupplyStatus.PENDING_APPROVAL.getCode().equals(this.statusEnum)) {
            return;
        }
        if (SupplyStatus.WITHDRAW.getCode().equals(this.statusEnum)) {
            return;
        }
        if (SupplyStatus.REJECT.getCode().equals(this.statusEnum)) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅待审核/已撤回/驳回状态允许提交审批");
    }

    /**
     * 同号全量修订是否允许的状态（BR-SR-003）：待审核、驳回、已撤回。
     *
     * <p>驳回/撤回后须可改明细再保存（重插后回落 {@link SupplyStatus#PENDING_APPROVAL}）或再提交，否则该态无业务闭环。
     * {@link SupplyStatus#AGREE} 等动账/审批通过终态仍禁止修订。</p>
     *
     * @param statusEnum 行上 {@code status_enum} 持久化 code
     * @return 允许修订时为 {@code true}
     */
    public static boolean isRevisableSupplyStatusCode(String statusEnum) {
        if (statusEnum == null || statusEnum.isBlank()) {
            return false;
        }
        String code = statusEnum.trim();
        if (SupplyStatus.PENDING_APPROVAL.getCode().equals(code)) {
            return true;
        }
        if (SupplyStatus.REJECT.getCode().equals(code)) {
            return true;
        }
        if (SupplyStatus.WITHDRAW.getCode().equals(code)) {
            return true;
        }
        return false;
    }

    /**
     * 进入「审核中」状态，可选写入申请时间。
     *
     * @param applyTime 申请/提交时间，可为 {@code null} 表示不覆盖
     */
    public void toApproval(LocalDateTime applyTime) {
        this.statusEnum = SupplyStatus.APPROVAL.getCode();
        if (applyTime != null) {
            this.applyTime = applyTime;
        }
    }

    /**
     * 撤回审批状态断言。
     *
     * @throws BusinessException 状态不允许
     */
    public void assertWithdrawable() {
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "statusEnum 不能为空");
        }
        if (!SupplyStatus.APPROVAL.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅审核中状态允许撤回");
        }
    }

    /**
     * 撤回审批后的状态流转（{@code status_enum} 置为 {@link SupplyStatus#WITHDRAW} 的持久化 {@code getCode()}）。
     */
    public void toWithdraw() {
        this.statusEnum = SupplyStatus.WITHDRAW.getCode();
    }

    /**
     * 同意状态断言。
     *
     * @throws BusinessException 状态不允许
     */
    public void assertApprovable() {
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "statusEnum 不能为空");
        }
        if (!SupplyStatus.APPROVAL.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅审核中状态允许同意");
        }
    }

    /**
     * 审批同意：迁至已通过，并记录审批人与审批时间。
     *
     * @param approverId 审批人主键
     * @param approvalTime 审批时间
     */
    public void approvalToAgree(Long approverId, LocalDateTime approvalTime) {
        this.statusEnum = SupplyStatus.AGREE.getCode();
        this.approverId = approverId;
        this.approvalTime = approvalTime;
    }

    /**
     * 驳回状态断言。
     *
     * @throws BusinessException 状态不允许
     */
    public void assertRejectable() {
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "statusEnum 不能为空");
        }
        if (!SupplyStatus.APPROVAL.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅审核中状态允许驳回");
        }
    }

    /**
     * 按业务单号逻辑删除前的状态断言（DM-WKF-006 §6.1.16；不变式 BR-SR-011）。
     *
     * <p>业务含义：已进入审批中、已同意或已产生下游事实的单据不得整单逻辑删除；仅终局前可撤销态允许删除。</p>
     *
     * @throws BusinessException 状态不允许删除
     */
    public void assertRemovableForSoftDelete() {
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "statusEnum 不能为空");
        }
        // 业务含义：待审核——建单后未提交或提交前可删单清理。
        if (SupplyStatus.PENDING_APPROVAL.getCode().equals(this.statusEnum)) {
            return;
        }
        // 业务含义：驳回——审批终局拒绝，允许删单重建。
        if (SupplyStatus.REJECT.getCode().equals(this.statusEnum)) {
            return;
        }
        // 业务含义：已撤回——从审核中主动撤回，回到可删态。
        if (SupplyStatus.WITHDRAW.getCode().equals(this.statusEnum)) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅待审核、驳回、已撤回状态允许删除单据");
    }

    /**
     * 门控：药库订货单同号下全量修订（软删旧行后重插）所要求的不变式（BR-SR-003 可编辑态 / 类型白名单）。
     *
     * <p>服务层不内联状态比较，统一经本方法委托。</p>
     *
     * @param lines 同一 {@code bus_no} 下已存在的未删除行
     * @throws BusinessException 非药库订货单或存在不可修订状态行（非待审核/驳回/已撤回）
     */
    public static void assertPurchaseOrderLinesRevisable(List<SupplyRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "订货单不存在，无法修订");
        }
        for (SupplyRequest row : lines) {
            if (row == null) {
                continue;
            }
            if (!SupplyType.CABINET_PURCHASE_ORDER.getCode().equals(row.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "非药库订货单不可按同号全量修订");
            }
            if (!SupplyRequest.isRevisableSupplyStatusCode(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅待审核/驳回/已撤回状态允许修订订货明细");
            }
        }
    }

    /**
     * 门控：药库进货单同号下全量修订（软删旧行后重插）所要求的不变式（BR-SR-003 可编辑态 / 类型白名单）。
     *
     * <p>{@code type_enum}、{@code status_enum} 与枚举比对时一律使用 {@link com.openhis.health.domain.share.enums.SupplyType#getCode()} /
     * {@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()} 持久化字符串（B.10.8.1），禁止排序号或数字串中转。</p>
     *
     * @param lines 同一 {@code bus_no} 下已存在的未删除行
     * @throws BusinessException 非药库进货单或存在不可修订状态行（非待审核/驳回/已撤回）
     */
    public static void assertInboundReceiptLinesRevisable(List<SupplyRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "进货单不存在，无法修订");
        }
        for (SupplyRequest row : lines) {
            if (row == null) {
                continue;
            }
            if (!SupplyType.CABINET_INBOUND_RECEIPT.getCode().equals(row.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "非药库进货单不可按同号全量修订");
            }
            if (!SupplyRequest.isRevisableSupplyStatusCode(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅待审核/驳回/已撤回状态允许修订进货明细");
            }
        }
    }

    /**
     * 门控：药库退货单（向供应商退货）同号下全量修订所要求的不变式。
     *
     * @param lines 同一 {@code bus_no} 下已存在的未删除行
     */
    public static void assertReturnToSupplierLinesRevisable(List<SupplyRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "退货单不存在，无法修订");
        }
        for (SupplyRequest row : lines) {
            if (row == null) {
                continue;
            }
            if (!SupplyType.CABINET_RETURN_TO_SUPPLIER.getCode().equals(row.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "非药库退货单不可按同号全量修订");
            }
            if (!SupplyRequest.isRevisableSupplyStatusCode(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅待审核/驳回/已撤回状态允许修订退货明细");
            }
        }
    }

    /**
     * 门控：药房请领单同号下全量修订（软删旧行后重插）所要求的不变式（PHA-QL-SDD-R-002 / BR-SR-003）。
     *
     * @param lines 同一 {@code bus_no} 下已存在的未删除行
     */
    public static void assertPharmacyRequisitionLinesRevisable(List<SupplyRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "请领单不存在，无法修订");
        }
        for (SupplyRequest row : lines) {
            if (row == null) {
                continue;
            }
            if (!SupplyType.PHARMACY_REQUISITION.getCode().equals(row.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "非药房请领单不可按同号全量修订");
            }
            if (!SupplyRequest.isRevisableSupplyStatusCode(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅待审核/驳回/已撤回状态允许修订请领明细");
            }
        }
    }

    /**
     * 门控：科室请领单（{@link SupplyType#DEPT_REQUISITION}）同号全量修订（与药房请领 PHA-QL-SDD-R-002 同构）。
     *
     * @param lines 同一 {@code bus_no} 下已存在的未删除行
     */
    public static void assertDeptRequisitionLinesRevisable(List<SupplyRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "科室请领单不存在，无法修订");
        }
        for (SupplyRequest row : lines) {
            if (row == null) {
                continue;
            }
            if (!SupplyType.DEPT_REQUISITION.getCode().equals(row.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "非科室请领单不可按同号全量修订");
            }
            if (!SupplyRequest.isRevisableSupplyStatusCode(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅待审核/驳回/已撤回状态允许修订科室请领明细");
            }
        }
    }

    /**
     * 门控：药房入库执行单（type 13）同号全量修订（PHA-IN-SDD-R-001）。
     */
    public static void assertPharmacyStockInLinesRevisable(List<SupplyRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "药房入库单不存在，无法修订");
        }
        for (SupplyRequest row : lines) {
            if (row == null) {
                continue;
            }
            if (!SupplyType.PHARMACY_STOCK_IN.getCode().equals(row.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "非药房入库单不可按同号全量修订");
            }
            if (!SupplyRequest.isRevisableSupplyStatusCode(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅待审核/驳回/已撤回状态允许修订药房入库明细");
            }
        }
    }

    /**
     * 门控：药房退库单（退回药库，type 14）同号全量修订。
     */
    public static void assertPharmacyReturnToWarehouseLinesRevisable(List<SupplyRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "药房退库单不存在，无法修订");
        }
        for (SupplyRequest row : lines) {
            if (row == null) {
                continue;
            }
            if (!SupplyType.PHARMACY_RETURN_TO_WAREHOUSE.getCode().equals(row.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "非药房退库单不可按同号全量修订");
            }
            if (!SupplyRequest.isRevisableSupplyStatusCode(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅待审核/驳回/已撤回状态允许修订药房退库明细");
            }
        }
    }

    /**
     * 门控：科室退库单（type 22）同号全量修订。
     *
     * @param lines 同一 {@code bus_no} 下已存在的未删除行
     * @throws BusinessException 单号不存在、非科室退库类型或存在不可修订状态行
     */
    public static void assertDeptReturnLinesRevisable(List<SupplyRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "科室退库单不存在，无法修订");
        }
        for (SupplyRequest row : lines) {
            if (row == null) {
                continue;
            }
            if (!SupplyType.DEPT_RETURN.getCode().equals(row.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "非科室退库单不可按同号全量修订");
            }
            if (!SupplyRequest.isRevisableSupplyStatusCode(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅待审核/驳回/已撤回状态允许修订科室退库明细");
            }
        }
    }

    /**
     * 门控：盘点执行单（药库/药房/科室盘点类 {@link SupplyType#isStocktakeTypeCode(String)}）同号全量修订。
     *
     * <p>与药库出库、订货修订一致：待审核/驳回/已撤回允许软删旧行后按命令全量重插；禁止混用非盘点类型单号。</p>
     *
     * @param lines 同一 {@code bus_no} 下已存在的未删除行
     * @throws BusinessException 单号不存在、非盘点类型或存在不可修订状态行
     */
    public static void assertStocktakeLinesRevisable(List<SupplyRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "盘点单不存在，无法修订");
        }
        for (SupplyRequest row : lines) {
            if (row == null) {
                continue;
            }
            if (!SupplyType.isStocktakeTypeCode(row.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "非盘点单不可按同号全量修订");
            }
            if (!SupplyRequest.isRevisableSupplyStatusCode(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "仅待审核/驳回/已撤回状态允许修订盘点明细");
            }
        }
    }

    /**
     * 门控：损益执行单（药库/药房/科室损益类 {@link SupplyType#isGainLossTypeCode(String)}）同号全量修订。
     *
     * <p>与盘点修订一致：待审核/驳回/已撤回允许软删旧行后按命令全量重插。</p>
     *
     * @param lines 同一 {@code bus_no} 下已存在的未删除行
     * @throws BusinessException 单号不存在、非损益类型或存在不可修订状态行
     */
    public static void assertGainLossLinesRevisable(List<SupplyRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "损益单不存在，无法修订");
        }
        for (SupplyRequest row : lines) {
            if (row == null) {
                continue;
            }
            if (!SupplyType.isGainLossTypeCode(row.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "非损益单不可按同号全量修订");
            }
            if (!SupplyRequest.isRevisableSupplyStatusCode(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "仅待审核/驳回/已撤回状态允许修订损益明细");
            }
        }
    }

    /**
     * 门控：库存期初执行单（{@link SupplyType#isOpeningTypeCode(String)}）同号全量修订。
     *
     * <p>待审核/驳回/已撤回允许软删旧行后按命令全量重插（AS-BAS-INV-001 IF-05）。</p>
     *
     * @param lines 同一 {@code bus_no} 下已存在的未删除行
     * @throws BusinessException 单号不存在、非期初类型或存在不可修订状态行
     */
    public static void assertOpeningLinesRevisable(List<SupplyRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "期初单不存在，无法修订");
        }
        for (SupplyRequest row : lines) {
            if (row == null) {
                continue;
            }
            if (!SupplyType.isOpeningTypeCode(row.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "非期初单不可按同号全量修订");
            }
            if (!SupplyRequest.isRevisableSupplyStatusCode(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "仅待审核/驳回/已撤回状态允许修订期初明细");
            }
        }
    }

    /**
     * 断言本行为当前有效已同意期初行，可作废为 {@link SupplyStatus#EXPIRED_INVALIDATED}。
     *
     * @throws BusinessException 非 OPENING、非 AGREE
     */
    public void assertOpeningAgreedVoidable() {
        if (!SupplyType.isOpeningTypeCode(this.typeEnum)) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅期初单允许作废回滚");
        }
        if (!SupplyStatus.AGREE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅已同意的期初单允许作废");
        }
    }

    /**
     * 将已同意期初行置为失效（覆盖/手工作废共用；不改库存，库存由编排层回滚）。
     */
    public void markOpeningExpiredInvalidated() {
        this.assertOpeningAgreedVoidable();
        this.statusEnum = SupplyStatus.EXPIRED_INVALIDATED.getCode();
    }

    /**
     * 门控：科室领用执行单（{@link com.openhis.health.domain.share.enums.SupplyType#DEPT_ISSUE}）同号全量修订。
     *
     * <p>与损益修订一致：待审核/驳回/已撤回允许软删旧行后按命令全量重插。</p>
     *
     * @param lines 同一 {@code bus_no} 下已存在的未删除行
     * @throws BusinessException 单号不存在、非领用类型或存在不可修订状态行
     */
    public static void assertDeptIssueLinesRevisable(List<SupplyRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "科室领用单不存在，无法修订");
        }
        for (SupplyRequest row : lines) {
            if (row == null) {
                continue;
            }
            if (!SupplyType.isDeptIssueTypeCode(row.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "非科室领用单不可按同号全量修订");
            }
            if (!SupplyRequest.isRevisableSupplyStatusCode(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "仅待审核/驳回/已撤回状态允许修订科室领用明细");
            }
        }
    }

    /**
     * 门控：科室退领执行单（{@link com.openhis.health.domain.share.enums.SupplyType#DEPT_ISSUE_RETURN}）同号全量修订。
     *
     * <p>与领用修订一致：待审核/驳回/已撤回允许软删旧行后按命令全量重插。</p>
     *
     * @param lines 同一 {@code bus_no} 下已存在的未删除行
     * @throws BusinessException 单号不存在、非退领类型或存在不可修订状态行
     */
    public static void assertDeptIssueReturnLinesRevisable(List<SupplyRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "科室退领单不存在，无法修订");
        }
        for (SupplyRequest row : lines) {
            if (row == null) {
                continue;
            }
            if (!SupplyType.isDeptIssueReturnTypeCode(row.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "非科室退领单不可按同号全量修订");
            }
            if (!SupplyRequest.isRevisableSupplyStatusCode(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "仅待审核/驳回/已撤回状态允许修订科室退领明细");
            }
        }
    }

    /**
     * 门控：科室采购申请单（{@link com.openhis.health.domain.share.enums.SupplyType#DEPT_PURCHASE_REQUEST}）同号全量修订。
     *
     * <p>与订货修订一致：待审核/驳回/已撤回允许软删旧行后按命令全量重插。</p>
     *
     * @param lines 同一 {@code bus_no} 下已存在的未删除行
     * @throws BusinessException 单号不存在、非采购申请类型或存在不可修订状态行
     */
    public static void assertDeptPurchaseRequestLinesRevisable(List<SupplyRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "科室采购申请单不存在，无法修订");
        }
        for (SupplyRequest row : lines) {
            if (row == null) {
                continue;
            }
            if (!SupplyType.isDeptPurchaseRequestTypeCode(row.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "非科室采购申请单不可按同号全量修订");
            }
            if (!SupplyRequest.isRevisableSupplyStatusCode(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "仅待审核/驳回/已撤回状态允许修订科室采购申请明细");
            }
        }
    }

    /**
     * 门控：药库出库执行单（{@link com.openhis.health.domain.share.enums.SupplyType#CABINET_STOCK_OUT}）同号全量修订（WHS-SO-SDD-R-003）。
     *
     * @param lines 同一 {@code bus_no} 下已存在的未删除行
     */
    public static void assertCabinetStockOutLinesRevisable(List<SupplyRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "出库单不存在，无法修订");
        }
        for (SupplyRequest row : lines) {
            if (row == null) {
                continue;
            }
            if (!SupplyType.CABINET_STOCK_OUT.getCode().equals(row.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "非药库出库单不可按同号全量修订");
            }
            if (!SupplyRequest.isRevisableSupplyStatusCode(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅待审核/驳回/已撤回状态允许修订出库明细");
            }
        }
    }

    /**
     * 门控：药库调出单（{@link com.openhis.health.domain.share.enums.SupplyType#CABINET_TRANSFER_OUT}）同号全量修订。
     *
     * @param lines 同一 {@code bus_no} 下已存在的未删除行
     */
    public static void assertCabinetTransferOutLinesRevisable(List<SupplyRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "药库调出单不存在，无法修订");
        }
        for (SupplyRequest row : lines) {
            if (row == null) {
                continue;
            }
            if (!SupplyType.CABINET_TRANSFER_OUT.getCode().equals(row.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "非药库调出单不可按同号全量修订");
            }
            if (!SupplyRequest.isRevisableSupplyStatusCode(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅待审核/驳回/已撤回状态允许修订药库调出明细");
            }
        }
    }

    /**
     * 门控：药房调出单（{@link com.openhis.health.domain.share.enums.SupplyType#PHARMACY_TRANSFER_OUT}）同号全量修订。
     *
     * @param lines 同一 {@code bus_no} 下已存在的未删除行
     */
    public static void assertPharmacyTransferOutLinesRevisable(List<SupplyRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "药房调出单不存在，无法修订");
        }
        for (SupplyRequest row : lines) {
            if (row == null) {
                continue;
            }
            if (!SupplyType.PHARMACY_TRANSFER_OUT.getCode().equals(row.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "非药房调出单不可按同号全量修订");
            }
            if (!SupplyRequest.isRevisableSupplyStatusCode(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅待审核/驳回/已撤回状态允许修订药房调出明细");
            }
        }
    }

    /**
     * 门控：科室调出单（{@link com.openhis.health.domain.share.enums.SupplyType#DEPT_TRANSFER_OUT}）同号全量修订。
     *
     * @param lines 同一 {@code bus_no} 下已存在的未删除行
     */
    public static void assertDeptTransferOutLinesRevisable(List<SupplyRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "科室调出单不存在，无法修订");
        }
        for (SupplyRequest row : lines) {
            if (row == null) {
                continue;
            }
            if (!SupplyType.DEPT_TRANSFER_OUT.getCode().equals(row.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "非科室调出单不可按同号全量修订");
            }
            if (!SupplyRequest.isRevisableSupplyStatusCode(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "仅待审核/驳回/已撤回状态允许修订科室调出明细");
            }
        }
    }

    /**
     * 门控：退库入库单（type 5）为系统壳单，禁止同号全量修订。
     */
    public static void assertCabinetReturnStockInLinesRevisable(List<SupplyRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_NOT_FOUND, "退库入库单不存在，无法修订");
        }
        throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
            "退库入库单由药房/科室退库同意后系统自动生成，不支持修订");
    }

    /**
     * 审批驳回：迁至已驳回，并记录审批人、审批时间与驳回原因（落 {@code reason}）。
     *
     * @param approverId 审批人主键
     * @param approvalTime 审批时间
     * @param rejectReason 驳回原因文案；可空（空则不清空既有行原因，由调用方决定是否覆盖）
     */
    public void approvalToReject(Long approverId, LocalDateTime approvalTime, String rejectReason) {
        this.statusEnum = SupplyStatus.REJECT.getCode();
        this.approverId = approverId;
        this.approvalTime = approvalTime;
        // 业务含义：驳回原因须落同号各行 reason，供列表/详情展示；无文案时仍写空串避免残留旧申请备注误判为驳回原因。
        if (rejectReason == null) {
            this.reason = null;
        } else {
            String trimmed = rejectReason.trim();
            if (trimmed.isEmpty()) {
                this.reason = null;
            } else {
                this.reason = trimmed;
            }
        }
    }
}