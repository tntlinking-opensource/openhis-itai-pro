/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.print.printtemplate.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.PrintTemplateStatus;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import com.zxplat.framework.core.domain.IMultiTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 画布打印模板聚合根（DM-PRT-001 · PrintTemplate）。
 *
 * <p>对应表 {@code prt_template}；状态机 draft → published → archived（BR-PRT-004）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "prt_template")
public class PrtTemplate extends BaseAuditedEntity implements IMultiTenant {

    /** 机构主键（本期租户级模板恒 NULL）。 */
    @Column(name = "organization_id")
    private Long organizationId;

    /** 模板业务键（PT + 日期 + 序号）。 */
    @Column(name = "bus_no", nullable = false, length = 64)
    private String busNo;

    /** 业务场景码（如 {@code outpatient.rx.print}）；published 行用于运行时按场景解析。 */
    @Column(name = "scenario_code", length = 64)
    private String scenarioCode;

    /** 模板展示名称。 */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** 模板状态码（{@link PrintTemplateStatus}）。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 模板业务版本（非乐观锁列）。 */
    @Column(name = "version", nullable = false)
    private Integer version;

    /** PrintJson 版式 JSON（hiprint 结构）。 */
    @Column(name = "print_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String printJson;

    /** PrintJson schemaVersion，缺省 1。 */
    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;

    /** 示例打印数据 JSON，设计态预览用。 */
    @Column(name = "sample_print_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String samplePrintData;

    /**
     * 断言当前行可作为 publish 源（BR-PRT-004）。
     *
     * @throws BusinessException 非 draft 或已软删
     */
    public void assertDraftForPublish() {
        if (!PrintTemplateStatus.DRAFT.sameAs(this.status)) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "当前状态不允许发布");
        }
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "已删除模板不允许发布");
        }
    }

    /**
     * 断言当前行可保存为 draft（SDD §5.1.1 · assertDraftStatus；BR-PRT-004）。
     *
     * @throws BusinessException 非 draft 或已软删
     */
    public void assertDraftStatus() {
        if (!PrintTemplateStatus.DRAFT.sameAs(this.status)) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "仅 draft 可保存");
        }
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "已删除模板不允许保存");
        }
    }

    /**
     * 断言当前行可作为 fork 源的 published 行。
     *
     * @throws BusinessException 非 published 或已软删
     */
    public void assertPublishedForFork() {
        if (!PrintTemplateStatus.PUBLISHED.sameAs(this.status)) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "源模板不是 published");
        }
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "已删除模板不可复制");
        }
    }

    /**
     * 断言租户归属一致，防止跨租户误写。
     *
     * @param tenantId 期望租户
     * @throws BusinessException 租户不匹配
     */
    public void assertTenantMatches(Long tenantId) {
        if (tenantId == null || !tenantId.equals(this.getTenantId())) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "模板租户不匹配");
        }
    }

    /**
     * 是否 archived 状态（场景码收回时跳过 archived 行）。
     *
     * @return archived 时为 true
     */
    public boolean isArchived() {
        return PrintTemplateStatus.ARCHIVED.sameAs(this.status);
    }

    /**
     * 迁移至 archived（publish 时旧 published 行收口）。
     */
    public void markArchived() {
        this.status = PrintTemplateStatus.ARCHIVED.getCode();
    }

    /**
     * 软删模板行。
     */
    public void markDeleted() {
        this.setIsDeleted(Boolean.TRUE);
    }
}
