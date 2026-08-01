/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.reportgrantext.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PeisGrantStatusConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * 体检报告发放扩展（表 {@code peis_report_grant_ext}，DM-PEIS-005）。
 *
 * <p>主键为 {@code diagnosticReportId}（FK as PK），不继承 {@code BaseAuditedEntity}。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "peis_report_grant_ext")
@Filter(name = "deletedFilter", condition = "is_deleted = :isDeleted")
public class PeisReportGrantExt implements Persistable<Long> {

    /** 总检报告主键（PK/FK）。 */
    @Id
    @Column(name = "diagnostic_report_id", nullable = false)
    private Long diagnosticReportId;

    /** 发放状态。 */
    @Column(name = "grant_status_enum", nullable = false)
    private String grantStatusEnum;

    /** 发放时间。 */
    @Column(name = "grant_time")
    private LocalDateTime grantTime;

    /** 发放人标识。 */
    @Column(name = "grant_by")
    private Long grantBy;

    /** 签收人姓名。 */
    @Column(name = "recipient_name")
    private String recipientName;

    /** 租户标识。 */
    @Column(name = "tenant_id")
    private Long tenantId;

    /** 院区标识。 */
    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Transient
    private boolean newEntity = true;

    @Override
    public Long getId() {
        return this.diagnosticReportId;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }

    /**
     * 手动设 PK 后标记为新建（走 persist）。
     */
    public void markNew() {
        this.newEntity = true;
    }

    /**
     * 登记发放（pending）。
     *
     * @param operatorId 操作人
     * @param now        操作时间
     */
    public void registerGrant(Long operatorId, LocalDateTime now) {
        this.grantStatusEnum = PeisGrantStatusConstants.PENDING;
        this.grantBy = operatorId;
        this.grantTime = now;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    /**
     * 签收（pending/printed → signed）。
     *
     * @param recipientName 签收人姓名（可选）
     * @param operatorId    操作人
     * @param now           操作时间
     */
    public void signGrant(String recipientName, Long operatorId, LocalDateTime now) {
        if (!PeisGrantStatusConstants.PENDING.equals(this.grantStatusEnum)
            && !PeisGrantStatusConstants.PRINTED.equals(this.grantStatusEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_GRANT_NOT_PENDING, "当前发放状态不可签收");
        }
        this.grantStatusEnum = PeisGrantStatusConstants.SIGNED;
        if (StringUtils.isNotEmpty(recipientName)) {
            this.recipientName = recipientName.trim();
        }
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    /**
     * 标记已打印（→ printed）。
     *
     * @param operatorId 操作人
     * @param now        操作时间
     */
    public void markPrinted(Long operatorId, LocalDateTime now) {
        if (PeisGrantStatusConstants.SIGNED.equals(this.grantStatusEnum)
            || PeisGrantStatusConstants.MAILED.equals(this.grantStatusEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_GRANT_ALREADY_SIGNED, "报告已签收，不可重复打印登记");
        }
        this.grantStatusEnum = PeisGrantStatusConstants.PRINTED;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    /**
     * 标记已邮寄（P3 桩：signed → mailed）。
     *
     * @param operatorId 操作人
     * @param now        操作时间
     */
    public void markMailed(Long operatorId, LocalDateTime now) {
        if (!PeisGrantStatusConstants.SIGNED.equals(this.grantStatusEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_GRANT_NOT_PENDING, "须先签收后再标记邮寄");
        }
        this.grantStatusEnum = PeisGrantStatusConstants.MAILED;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }
}
