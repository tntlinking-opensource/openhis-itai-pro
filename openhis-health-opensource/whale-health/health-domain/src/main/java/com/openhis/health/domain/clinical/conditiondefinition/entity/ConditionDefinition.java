/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.conditiondefinition.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.openhis.whale.core.application.util.StringUtils;

/**
 * 诊断定义聚合根。
 */
@Getter
@Setter
@Entity
@Table(name = "cln_condition_definition")
public class ConditionDefinition extends BaseAuditedEntity {
    @Column(name = "source_enum", nullable = false)
    private String sourceEnum;
    @Column(name = "condition_code", nullable = false)
    private String conditionCode;
    @Column(name = "name", nullable = false)
    private String name;
    @Column(name = "py_str")
    private String pyStr;
    @Column(name = "wb_str")
    private String wbStr;
    @Column(name = "type_code")
    private String typeCode;
    @Column(name = "description")
    private String description;
    @Column(name = "yb_flag")
    private Boolean ybFlag;
    @Column(name = "yb_no")
    private String ybNo;
    @Column(name = "yb_match_flag")
    private Boolean ybMatchFlag;
    /** 国家临床版诊断编码（国临码）；病案首页用，医保上传仍走 {@link #ybNo}。 */
    @Column(name = "clinic_code")
    private String clinicCode;
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /**
     * 校验核心参数。
     */
    public void assertCoreFields() {
        if (StringUtils.isEmpty(this.conditionCode) || StringUtils.isEmpty(this.name) || StringUtils.isEmpty(this.sourceEnum)) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_INVALID_ARGUMENT, "诊断定义关键字段不能为空");
        }
    }

    /**
     * 重建检索码（仅当助记码为空时从名称生成，避免覆盖手工录入）。
     * <p>拼音助记为名称的拼音首字母（大写）；五笔助记为名称的五笔首字母（大写），与 {@link SearchCodeUtil} 口径一致。</p>
     */
    public void rebuildSearchKey() {
        if (StringUtils.isEmpty(this.name)) {
            return;
        }
        if (StringUtils.isEmpty(this.pyStr)) {
            this.pyStr = SearchCodeUtil.toPyStr(this.name);
        }
        if (StringUtils.isEmpty(this.wbStr)) {
            this.wbStr = SearchCodeUtil.toWbStr(this.name);
        }
    }

    /**
     * 置为启用状态。
     */
    public void enable() {
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
    }

    /**
     * 置为停用状态。
     */
    public void disable() {
        this.statusEnum = PublicationStatus.INACTIVE.getCode();
    }
}
