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
import com.openhis.health.domain.share.enums.PriceType;
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
 * 价格调整聚合根。
 *
 * <p>记录价格变更的审批流程与历史事实。已生效（approved）行视为不可变历史事实，不允许再修改。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cus_price_adjustment")
public class PriceAdjustment extends BaseAuditedEntity {

    @Column(name = "definition_id", nullable = false)
    private Long definitionId;

    @Column(name = "price_type", nullable = false)
    private String priceType;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "old_amount", nullable = false)
    private BigDecimal oldAmount;

    @Column(name = "new_amount", nullable = false)
    private BigDecimal newAmount;

    @Column(name = "adjustment_reason", nullable = false)
    private String adjustmentReason;

    @Column(name = "adjustment_time", nullable = false)
    private Date adjustmentTime;

    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    @Column(name = "submitter_id")
    private Long submitterId;

    @Column(name = "submit_time")
    private Date submitTime;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "approve_time")
    private Date approveTime;

    @Column(name = "approve_comment")
    private String approveComment;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    @Column(name = "adjustment_delta")
    private BigDecimal adjustmentDelta;

    @Column(name = "total_impact")
    private BigDecimal totalImpact;

    /**
     * 断言必填字段与核心约束（BR-PRC-006/007/008/009/010/011/013/014）。
     */
    public void assertRequiredFields() {
        if (this.definitionId == null) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "定价记录标识不能为空");
        }
        if (this.priceType == null || this.priceType.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "价格类型不能为空");
        }
        if (this.oldAmount == null || this.newAmount == null) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "调价前后金额必须同时存在");
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
        if (PriceType.isLotNumberPriceType(this.priceType)) {
            if (this.lotNumber == null || this.lotNumber.trim().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "批号价变更时批次号不能为空");
            }
        }
        this.assertDeltaConsistent();
        this.assertStockQuantityPresentWhenLotNumber();
    }

    /**
     * 断言已生效记录不可变（BR-PRC-004）。
     */
    public void assertNotApproved() {
        if (PriceAdjustmentStatus.APPROVED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_STATUS, "已生效记录不允许修改");
        }
    }

    /**
     * 断言差异金额与金额字段一致（BR-PRC-013）。
     */
    public void assertDeltaConsistent() {
        BigDecimal expected = this.newAmount.subtract(this.oldAmount);
        if (this.adjustmentDelta == null) {
            this.adjustmentDelta = expected;
            return;
        }
        if (this.adjustmentDelta.compareTo(expected) != 0) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "调整差异金额与调价前后金额不一致");
        }
    }

    /**
     * 断言批号价变更库存数量已记录（BR-PRC-014）。
     */
    public void assertStockQuantityPresentWhenLotNumber() {
        if (!PriceType.isLotNumberPriceType(this.priceType)) {
            return;
        }
        if (this.stockQuantity == null) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_ARGUMENT, "批号价变更时库存数量不能为空");
        }
    }

    /**
      * 初始化领域状态。
     */
    public void initDraft() {
        this.statusEnum = PriceAdjustmentStatus.DRAFT.getCode();
    }

    /**
     * 提交审批：草稿/已驳回可提交，记录提交人与提交时间并迁至待审。
     *
     * @param submitterId 提交人主键
     * @param submitTime 提交时间，{@code null} 则取当前时间
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
     * 审批通过：待审态可执行，记录审批人、意见与时间。
     *
     * @param approverId 审批人主键
     * @param approveComment 审批意见
     * @param approveTime 审批时间，{@code null} 则取当前时间
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
     * 审批拒绝：待审态可执行，意见必填，记录审批人与时间并迁至已驳回。
     *
     * @param approverId 审批人主键
     * @param approveComment 拒绝原因（必填）
     * @param approveTime 审批时间，{@code null} 则取当前时间
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
      * 处理withdraw逻辑。
     */
    public void withdraw() {
        if (!PriceAdjustmentStatus.DRAFT.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_INVALID_STATUS, "当前状态不允许撤销草稿");
        }
        this.statusEnum = PriceAdjustmentStatus.DEPRECATED.getCode();
    }
}

