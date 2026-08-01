/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.activityobservationlink.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 项目 AD ↔ 小项 OD 绑定行（表 {@code cus_activity_observation_link}，DM-CUS-013）。
 *
 * <p>聚合边界按 {@code activityDefinitionId}：{@code saveLinks} 全量对齐该项目下链接行。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cus_activity_observation_link")
public class ActivityObservationLink extends BaseAuditedEntity {

    /** 院区/机构标识。 */
    @Column(name = "org_id")
    private Long orgId;

    /** 项目 ActivityDefinition 主键。 */
    @Column(name = "activity_definition_id", nullable = false)
    private Long activityDefinitionId;

    /** 小项 ObservationDefinition 主键。 */
    @Column(name = "observation_definition_id", nullable = false)
    private Long observationDefinitionId;

    /** 显示排序号。 */
    @Column(name = "sort_num", nullable = false)
    private Integer sortNum;

    /** 是否必检小项。 */
    @Column(name = "required_flag", nullable = false)
    private Boolean requiredFlag;

    /**
     * 校验绑定行必填字段。
     *
     * @throws BusinessException 关键字段缺失时
     */
    public void assertCoreFields() {
        if (this.activityDefinitionId == null || this.observationDefinitionId == null || this.sortNum == null) {
            throw new BusinessException(
                ErrorCodeConstants.ACTIVITY_OBSERVATION_LINK_INVALID_ARGUMENT,
                "项目、小项与排序号不能为空");
        }
        if (this.requiredFlag == null) {
            this.requiredFlag = Boolean.TRUE;
        }
    }

    /**
     * 逻辑删除绑定行（从项目绑定集移除）。
     *
     * @param operatorId 操作人；可空
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }
}
