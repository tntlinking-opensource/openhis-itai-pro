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

package com.openhis.health.domain.custom.priceadjustment.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PriceAdjustmentStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 调价单项目行（DM-CUS-011 v2，表 {@code cus_price_adjustment}）。
 *
 * <p>同一 {@code bus_no} 可多行（每项目一行），共享审批状态与提交/审批字段。</p>
 * <p>药品耗材与诊疗共用本表；诊疗 {@code item_table} 为活动/医疗服务，仅目标售价。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cus_price_adjustment")
public class PriceAdjustment extends BaseAuditedEntity {

    /** 调价单号。 */
    @Column(name = "bus_no", nullable = false)
    private String busNo;

    /** 项目物理表名。 */
    @Column(name = "item_table", nullable = false)
    private String itemTable;

    /** 项目定义主键。 */
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    /** 目标进价。 */
    @Column(name = "new_purchase_amount")
    private BigDecimal newPurchaseAmount;

    /** 目标售价。 */
    @Column(name = "new_selling_amount")
    private BigDecimal newSellingAmount;

    /** 调价原因。 */
    @Column(name = "adjustment_reason", nullable = false)
    private String adjustmentReason;

    /** 制单/申请时间。 */
    @Column(name = "adjustment_time", nullable = false)
    private Date adjustmentTime;

    /** 审批状态。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /** 提交人。 */
    @Column(name = "submitter_id")
    private Long submitterId;

    /** 提交时间。 */
    @Column(name = "submit_time")
    private Date submitTime;

    /** 审批人。 */
    @Column(name = "approver_id")
    private Long approverId;

    /** 审批时间。 */
    @Column(name = "approve_time")
    private Date approveTime;

    /** 审批意见。 */
    @Column(name = "approve_comment")
    private String approveComment;

    /** 操作人。 */
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    /** 机构。 */
    @Column(name = "org_id", nullable = false)
    private Long orgId;

    /**
     * 校验草稿/驳回态可编辑字段（BR-PRC-004）。
     */
    public void assertEditable() {
        if (PriceAdjustmentStatus.APPROVED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_STATUS, "已生效记录不允许修改");
        }
        if (PriceAdjustmentStatus.PENDING.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_STATUS, "待审批记录不允许修改");
        }
        if (PriceAdjustmentStatus.DEPRECATED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_STATUS, "已废弃记录不允许修改");
        }
    }

    /**
     * 校验制单必填与目标价侧（BR-PRC-006～009）。
     */
    public void assertRequiredFields() {
        if (this.busNo == null || this.busNo.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "调价单号不能为空");
        }
        if (this.itemTable == null || this.itemTable.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "项目表名不能为空");
        }
        if (this.itemId == null) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "项目标识不能为空");
        }
        if (this.newPurchaseAmount == null && this.newSellingAmount == null) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "目标进价与目标售价至少填写一项");
        }
        if (this.adjustmentReason == null || this.adjustmentReason.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "调整原因不能为空");
        }
        if (this.operatorId == null) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "操作人不能为空");
        }
        if (this.orgId == null) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "机构不能为空");
        }
        if (this.adjustmentTime == null) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "调整时间不能为空");
        }
        if (this.adjustmentTime.after(new Date())) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "调整时间不能晚于当前时间");
        }
    }

    /**
     * 初始化为草稿态。
     */
    public void initDraft() {
        this.statusEnum = PriceAdjustmentStatus.DRAFT.getCode();
    }

    /**
     * 提交审批：草稿/已驳回可提交。
     *
     * @param submitterId 提交人
     * @param submitTime  提交时间，空则取当前
     */
    public void submit(Long submitterId, Date submitTime) {
        if (!PriceAdjustmentStatus.DRAFT.getCode().equals(this.statusEnum)
            && !PriceAdjustmentStatus.REJECTED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_STATUS, "当前状态不允许提交审批");
        }
        if (submitterId == null) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "提交人不能为空");
        }
        this.submitterId = submitterId;
        if (submitTime == null) {
            this.submitTime = new Date();
        } else {
            this.submitTime = submitTime;
        }
        this.statusEnum = PriceAdjustmentStatus.PENDING.getCode();
    }

    /**
     * 审批通过。
     *
     * @param approverId     审批人
     * @param approveComment 意见
     * @param approveTime    审批时间
     */
    public void approve(Long approverId, String approveComment, Date approveTime) {
        if (!PriceAdjustmentStatus.PENDING.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_STATUS, "当前状态不允许审批通过");
        }
        if (approverId == null) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "审批人不能为空");
        }
        this.approverId = approverId;
        this.approveComment = approveComment;
        if (approveTime == null) {
            this.approveTime = new Date();
        } else {
            this.approveTime = approveTime;
        }
        this.statusEnum = PriceAdjustmentStatus.APPROVED.getCode();
    }

    /**
     * 审批驳回。
     *
     * @param approverId     审批人
     * @param approveComment 驳回原因（必填）
     * @param approveTime    审批时间
     */
    public void reject(Long approverId, String approveComment, Date approveTime) {
        if (!PriceAdjustmentStatus.PENDING.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_STATUS, "当前状态不允许审批拒绝");
        }
        if (approverId == null) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "审批人不能为空");
        }
        if (approveComment == null || approveComment.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "审批意见不能为空");
        }
        this.approverId = approverId;
        this.approveComment = approveComment;
        if (approveTime == null) {
            this.approveTime = new Date();
        } else {
            this.approveTime = approveTime;
        }
        this.statusEnum = PriceAdjustmentStatus.REJECTED.getCode();
    }

    /**
     * 撤销草稿。
     */
    public void withdraw() {
        if (!PriceAdjustmentStatus.DRAFT.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_STATUS, "当前状态不允许撤销草稿");
        }
        this.statusEnum = PriceAdjustmentStatus.DEPRECATED.getCode();
    }
}
