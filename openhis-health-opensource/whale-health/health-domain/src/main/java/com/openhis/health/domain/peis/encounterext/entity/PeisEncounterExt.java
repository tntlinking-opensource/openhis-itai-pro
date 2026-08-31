/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.encounterext.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
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
 * 体检就诊扩展（表 {@code peis_encounter_ext}，DM-PEIS-007）。
 *
 * <p>主键为 {@code encounterId}（FK as PK），不继承 {@code BaseAuditedEntity}。
 * 审计字段由 DomainService 写入；勿挂 {@code AuditingEntityListener}（其参数类型为 AuditedEntityBase）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "peis_encounter_ext")
/** 复用 AuditedEntityBase 已注册的 deletedFilter，勿再声明 @FilterDef。 */
@Filter(name = "deletedFilter", condition = "is_deleted = :isDeleted")
public class PeisEncounterExt implements Persistable<Long> {

    /** 就诊主键（PK/FK）。 */
    @Id
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /** 套餐 ActivityDefinition 主键。 */
    @Column(name = "package_activity_id")
    private Long packageActivityId;

    /** 套餐快照 JSON。 */
    @Column(name = "package_snapshot_json")
    private String packageSnapshotJson;

    /** 是否团检。 */
    @Column(name = "group_flag", nullable = false)
    private Boolean groupFlag;

    /** 团检单位。 */
    @Column(name = "company_id")
    private Long companyId;

    /** 团检合同。 */
    @Column(name = "contract_id")
    private Long contractId;

    /** 团检批次。 */
    @Column(name = "group_batch_id")
    private Long groupBatchId;

    /** 预约主键。 */
    @Column(name = "booking_appointment_id")
    private Long bookingAppointmentId;

    /** 体检流水号。 */
    @Column(name = "bus_no")
    private String busNo;

    /** 本次体检推荐人。 */
    @Column(name = "referrer_name")
    private String referrerName;

    /** 本次体检是否提供早餐。 */
    @Column(name = "breakfast_flag")
    private Boolean breakfastFlag;

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
        return this.encounterId;
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
     * 校验团检时单位与分组必填（BR-PE-EXT-02）。
     */
    public void assertGroupCompany() {
        if (!Boolean.TRUE.equals(this.groupFlag)) {
            return;
        }
        if (this.companyId == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_ENC_EXT_INVALID_ARGUMENT, "团检须指定单位");
        }
        if (this.groupBatchId == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_ENC_EXT_INVALID_ARGUMENT, "团检须指定分组");
        }
    }

    /**
     * 刷新套餐快照。
     *
     * @param packageActivityId    套餐 AD
     * @param packageSnapshotJson  快照 JSON
     */
    public void updatePackageSnapshot(Long packageActivityId, String packageSnapshotJson) {
        this.packageActivityId = packageActivityId;
        this.packageSnapshotJson = packageSnapshotJson;
    }
}
