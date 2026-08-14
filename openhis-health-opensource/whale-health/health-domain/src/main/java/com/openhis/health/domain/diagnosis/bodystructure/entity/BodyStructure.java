/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.diagnosis.bodystructure.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
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
 * 身体部位聚合根。
 */
@Getter
@Setter
@Entity
@Table(name = "dia_body_structure")
public class BodyStructure extends BaseAuditedEntity {

    @Column(name = "bus_no", nullable = false)
    private String busNo;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    @Column(name = "py_str")
    private String pyStr;

    @Column(name = "wb_str")
    private String wbStr;

    @Column(name = "parent_id")
    private Long parentId;

    /**
      * 断言业务前置条件满足。
     */
    public void assertNameNotBlank() {
        if (StringUtils.isEmpty(this.name)) {
            throw new BusinessException(ErrorCodeConstants.BODY_STRUCTURE_INVALID_ARGUMENT, "身体部位名称不能为空");
        }
    }

    /**
      * 断言业务前置条件满足。
     */
    public void assertBusNoGenerated() {
        if (StringUtils.isEmpty(this.busNo)) {
            throw new BusinessException(ErrorCodeConstants.BODY_STRUCTURE_INVALID_ARGUMENT, "身体部位业务编码不能为空");
        }
    }

    /**
     * 按名称重算拼音/五笔检索码（BR-BDS-003：名称变更后强制同步）。
     */
    public void refreshSearchCodeFromName() {
        // 名称为空时不覆盖既有检索码，避免误清空
        if (StringUtils.isEmpty(this.name)) {
            return;
        }
        this.pyStr = SearchCodeUtil.toPyStr(this.name);
        this.wbStr = SearchCodeUtil.toWbStr(this.name);
    }

    /**
     * 断言当前为启用状态，仅启用状态可执行停用操作。
     */
    public void assertActive() {
        if (!PublicationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.BODY_STRUCTURE_INVALID_STATUS, "仅启用状态可停用");
        }
    }

    /**
      * 将状态切换为启用。
     */
    public void activate() {
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
    }

    /**
      * 处理inactivate逻辑。
     */
    public void inactivate() {
        this.statusEnum = PublicationStatus.INACTIVE.getCode();
    }

    /**
      * 断言业务前置条件满足。
     */
    public void assertSelectableWhenActive() {
        if (!PublicationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.BODY_STRUCTURE_INVALID_STATUS, "仅启用状态的身体部位可用于业务选择");
        }
    }

    /**
     * 逻辑删除身体部位并记录操作人。
     *
     * @param operatorId 操作人主键
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }
}
