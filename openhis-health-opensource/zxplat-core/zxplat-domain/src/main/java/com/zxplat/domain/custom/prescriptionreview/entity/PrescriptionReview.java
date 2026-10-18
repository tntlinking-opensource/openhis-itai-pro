/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.prescriptionreview.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.PrescriptionReviewStatus;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 处方点评聚合根（表 {@code cus_prescription_review}，DM-CUS-015）。
 *
 * <p>有效行一对一锚定 {@code prescription_no}；撤销软删后可再插入新行。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cus_prescription_review")
public class PrescriptionReview extends BaseAuditedEntity {

    /** 点评业务号（本聚合 PRV 日序自生成，非 MR/EN 快照）。 */
    @Column(name = "bus_no", nullable = false)
    private String busNo;

    /** 处方号（有效行唯一锚点）。 */
    @Column(name = "prescription_no", nullable = false)
    private String prescriptionNo;

    /** 点评人。 */
    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    /** 点评时间。 */
    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;

    /** 是否合理。 */
    @Column(name = "reasonable_flag", nullable = false)
    private Boolean reasonableFlag;

    /** 点评状态（有效行固定 REVIEWED）。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /** 原因（不合理必填）。 */
    @Column(name = "reason")
    private String reason;

    /** 备注。 */
    @Column(name = "remark")
    private String remark;

    /**
     * 写入已点评状态（BR-PRV-004）：有效落库行固定 {@code REVIEWED}，禁止持久化 PENDING。
     */
    public void markReviewed() {
        this.statusEnum = PrescriptionReviewStatus.REVIEWED.getCode();
    }

    /**
     * 断言创建必填字段（BR-PRV-005）。
     *
     * @throws BusinessException 必填缺失时
     */
    public void assertRequiredFields() {
        // 点评业务号缺失则无法对外展示/检索，禁止落库
        if (StringUtils.isBlank(this.busNo)) {
            throw new BusinessException(ErrorCodeConstants.PRESCRIPTION_REVIEW_INVALID_ARGUMENT, "点评业务号不能为空");
        }
        // 处方号是有效行唯一锚点，缺失无法一对一
        if (StringUtils.isBlank(this.prescriptionNo)) {
            throw new BusinessException(ErrorCodeConstants.PRESCRIPTION_REVIEW_INVALID_ARGUMENT, "处方号不能为空");
        }
        // 点评人缺失则无法追溯谁评
        if (this.reviewerId == null) {
            throw new BusinessException(ErrorCodeConstants.PRESCRIPTION_REVIEW_INVALID_ARGUMENT, "点评人不能为空");
        }
        // 点评时间缺失则列表/导出时间轴断裂
        if (this.reviewedAt == null) {
            throw new BusinessException(ErrorCodeConstants.PRESCRIPTION_REVIEW_INVALID_ARGUMENT, "点评时间不能为空");
        }
        // 是否合理是点评结论核心字段
        if (this.reasonableFlag == null) {
            throw new BusinessException(ErrorCodeConstants.PRESCRIPTION_REVIEW_INVALID_ARGUMENT, "是否合理不能为空");
        }
    }

    /**
     * 不合理时断言原因必填（BR-PRV-002）。
     *
     * @throws BusinessException 不合理且原因为空时
     */
    public void assertReasonWhenUnreasonable() {
        // 判定不合理时原因必填，否则审方结论无法复核（BR-PRV-002）
        if (Boolean.FALSE.equals(this.reasonableFlag) && StringUtils.isBlank(this.reason)) {
            throw new BusinessException(ErrorCodeConstants.PRESCRIPTION_REVIEW_REASON_REQUIRED, "不合理时原因必填");
        }
    }

    /**
     * 断言当前行可撤销（有效且已点评）。
     *
     * @throws BusinessException 状态非法时
     */
    public void assertCanRevoke() {
        // 已软删行不可再撤，避免重复撤销
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.PRESCRIPTION_REVIEW_NOT_FOUND, "有效点评不存在");
        }
        // 仅已点评有效行可撤；非法状态拒绝以免污染展示态
        if (!PrescriptionReviewStatus.REVIEWED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRESCRIPTION_REVIEW_INVALID_ARGUMENT, "仅已点评可撤销");
        }
    }

    /**
     * 软删本行（撤销点评）。
     *
     * @param operatorId 操作人
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }
}
