/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.servicerequestext.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.PeisDispatchStatusConstants;
import com.zxplat.domain.share.constants.PeisExecStatusConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
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
 * 体检服务申请执行扩展（表 {@code peis_service_request_ext}，DM-PEIS-001）。
 *
 * <p>主键为 {@code serviceRequestId}（FK as PK），不继承 {@code BaseAuditedEntity}。
 * 审计字段由 DomainService 写入；勿挂 {@code AuditingEntityListener}（其参数类型为 AuditedEntityBase）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "peis_service_request_ext")
/** 复用 AuditedEntityBase 已注册的 deletedFilter，勿再声明 @FilterDef。 */
@Filter(name = "deletedFilter", condition = "is_deleted = :isDeleted")
public class PeisServiceRequestExt implements Persistable<Long> {

    /** 服务申请主键（PK/FK）。 */
    @Id
    @Column(name = "service_request_id", nullable = false)
    private Long serviceRequestId;

    /** 执行状态。 */
    @Column(name = "exec_status_enum", nullable = false)
    private String execStatusEnum;

    /** LIS 外发状态。 */
    @Column(name = "lis_dispatch_enum", nullable = false)
    private String lisDispatchEnum;

    /** PACS 外发状态。 */
    @Column(name = "pacs_dispatch_enum", nullable = false)
    private String pacsDispatchEnum;

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
        return this.serviceRequestId;
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
     * 标记已检。
     */
    public void markChecked() {
        this.execStatusEnum = PeisExecStatusConstants.CHECKED;
    }

    /**
     * 标记弃检（须填原因，原因不落库）。
     *
     * @param reason 弃检原因
     */
    public void markAbandoned(String reason) {
        if (StringUtils.isEmpty(reason)) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_SR_EXT_INVALID_ARGUMENT, "弃检原因不能为空");
        }
        this.execStatusEnum = PeisExecStatusConstants.ABANDONED;
    }

    /**
     * 标记延检（须填原因，原因不落库）。
     *
     * @param reason 延检原因
     */
    public void markDeferred(String reason) {
        if (StringUtils.isEmpty(reason)) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_SR_EXT_INVALID_ARGUMENT, "延检原因不能为空");
        }
        this.execStatusEnum = PeisExecStatusConstants.DEFERRED;
    }

    /**
     * 更新 LIS 外发状态。
     *
     * @param code 外发状态 code
     */
    public void updateLisDispatch(String code) {
        assertDispatchCode(code);
        this.lisDispatchEnum = code;
    }

    /**
     * 更新 PACS 外发状态。
     *
     * @param code 外发状态 code
     */
    public void updatePacsDispatch(String code) {
        assertDispatchCode(code);
        this.pacsDispatchEnum = code;
    }

    /**
     * 填充新建默认执行/外发态。
     */
    public void applyInitDefaults() {
        this.execStatusEnum = PeisExecStatusConstants.PENDING;
        this.lisDispatchEnum = PeisDispatchStatusConstants.NOT_SENT;
        this.pacsDispatchEnum = PeisDispatchStatusConstants.NOT_SENT;
        if (this.isDeleted == null) {
            this.isDeleted = Boolean.FALSE;
        }
    }

    private static void assertDispatchCode(String code) {
        if (StringUtils.isEmpty(code)) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_SR_EXT_INVALID_ARGUMENT, "外发状态不能为空");
        }
        if (!PeisDispatchStatusConstants.NOT_SENT.equals(code)
            && !PeisDispatchStatusConstants.SENT.equals(code)
            && !PeisDispatchStatusConstants.RESULT_RECEIVED.equals(code)) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_SR_EXT_INVALID_ARGUMENT, "外发状态非法");
        }
    }
}
