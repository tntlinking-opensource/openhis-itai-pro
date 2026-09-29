/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.proceduredefinition.entity;

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

/**
 * 手术操作定义聚合根（国临 ICD-9-CM3 / 医保对照目录）。
 */
@Getter
@Setter
@Entity
@Table(name = "cln_procedure_definition")
public class ProcedureDefinition extends BaseAuditedEntity {

    @Column(name = "source_enum", nullable = false)
    private String sourceEnum;
    @Column(name = "procedure_code", nullable = false)
    private String procedureCode;
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
    /** 国家临床版手术编码（国临码）；病案首页用，医保上传走 {@link #ybNo}。 */
    @Column(name = "clinic_code")
    private String clinicCode;
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /**
     * 挂靠诊疗项目定义主键（{@code wkf_activity_definition.id}）。
     *
     * <p>国临选品后取本字段生成申请行计费（DM-CLN-009 BR-PD-010）；可空，未绑定时开立落行拒绝。</p>
     */
    @Column(name = "activity_definition_id")
    private Long activityDefinitionId;

    public void assertCoreFields() {
        if (StringUtils.isEmpty(this.procedureCode) || StringUtils.isEmpty(this.name) || StringUtils.isEmpty(this.sourceEnum)) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_DEFINITION_INVALID_ARGUMENT, "手术定义关键字段不能为空");
        }
    }

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

    public void enable() {
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
    }

    public void disable() {
        this.statusEnum = PublicationStatus.INACTIVE.getCode();
    }
}
