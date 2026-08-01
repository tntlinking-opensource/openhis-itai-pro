/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.observationdefinition.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.ObservationDefinitionConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 临床观察定义聚合根（表 {@code cln_observation_definition}，DM-CLN-005）。
 *
 * <p>目录项：体检小项、分科结论 OD 等；实例事实见 DM-CLN-007，本聚合不写 {@code cln_observation}。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cln_observation_definition")
public class ObservationDefinition extends BaseAuditedEntity {

    /** 院区/机构标识。 */
    @Column(name = "org_id")
    private Long orgId;

    /** 院内观察项编码（租户内唯一，BR-CLN-OD-01）。 */
    @Column(name = "code", nullable = false)
    private String code;

    /** 展示名称。 */
    @Column(name = "display", nullable = false)
    private String display;

    /** 值类型（quantity / string / codeable / boolean）。 */
    @Column(name = "value_type_enum", nullable = false)
    private String valueTypeEnum;

    /** 默认计量单位编码。 */
    @Column(name = "unit_code")
    private String unitCode;

    /** 分类编码（exam_item / vitals / dept_conclusion / other）。 */
    @Column(name = "category_code", nullable = false)
    private String categoryCode;

    /** 发布状态（active / inactive）。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /**
     * 校验创建/更新必填字段。
     *
     * @throws BusinessException 关键字段缺失时
     */
    public void assertCoreFields() {
        if (StringUtils.isEmpty(this.code)
            || StringUtils.isEmpty(this.display)
            || StringUtils.isEmpty(this.valueTypeEnum)
            || StringUtils.isEmpty(this.categoryCode)) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_DEFINITION_INVALID_ARGUMENT,
                "观察定义编码、名称、值类型与分类不能为空");
        }
    }

    /**
     * 校验分类码属于允许集合（BR-CLN-OD-03）。
     *
     * @throws BusinessException 分类非法时
     */
    public void assertCategoryAllowed() {
        if (!ObservationDefinitionConstants.ALLOWED_CATEGORY_CODES.contains(this.categoryCode)) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_DEFINITION_INVALID_CATEGORY,
                "观察定义分类编码不在允许范围");
        }
    }

    /**
     * 置为启用。
     */
    public void activate() {
        this.statusEnum = ObservationDefinitionConstants.STATUS_ACTIVE;
    }

    /**
     * 置为停用（active → inactive）。
     *
     * @throws BusinessException 当前非启用态时
     */
    public void deactivate() {
        if (!ObservationDefinitionConstants.STATUS_ACTIVE.equals(this.statusEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_DEFINITION_INVALID_STATUS,
                "仅启用态观察定义可停用");
        }
        this.statusEnum = ObservationDefinitionConstants.STATUS_INACTIVE;
    }

    /**
     * 逻辑删除本目录行。
     *
     * @param operatorId 操作人
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }
}
