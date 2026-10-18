/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.commonresult.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.PeisCatalogConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * PEIS 常见结果模板（表 {@code peis_common_result}，DM-PEIS-003）。
 */
@Getter
@Setter
@Entity
@Table(name = "peis_common_result")
public class PeisCommonResult extends BaseAuditedEntity {

    /** 绑定小项观察定义。 */
    @Column(name = "observation_definition_id")
    private Long observationDefinitionId;

    /** 科室机构标识。 */
    @Column(name = "dept_org_id")
    private Long deptOrgId;

    /** 模板名称。 */
    @Column(name = "display", nullable = false)
    private String display;

    /** 默认结果值。 */
    @Column(name = "value_string")
    private String valueString;

    /** 解读码（H/L/N 等）。 */
    @Column(name = "interpretation_code")
    private String interpretationCode;

    /** 排序号。 */
    @Column(name = "sort_num", nullable = false)
    private Integer sortNum;

    /** 业务状态（active / inactive）。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /** 院区标识。 */
    @Column(name = "org_id")
    private Long orgId;

    /**
     * 校验核心字段。
     */
    public void assertCoreFields() {
        if (StringUtils.isEmpty(this.display) || this.sortNum == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_COMMON_RESULT_INVALID_ARGUMENT,
                "常见结果名称与排序号不能为空");
        }
    }

    /**
     * 停用（active → inactive，BR-PCR-02）。
     */
    public void deactivate() {
        this.statusEnum = PeisCatalogConstants.COMMON_RESULT_STATUS_INACTIVE;
    }
}
