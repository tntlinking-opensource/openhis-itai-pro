/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.registrationdetail.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊挂号明细报表页初始化响应（IF-RPT-ORG-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊挂号明细报表页初始化响应")
public class LoadOutpatientRegistrationDetailReportPageInitResponse {

    /** 默认查询日起（yyyy-MM-dd，当日）。 */
    @Schema(description = "默认查询日起")
    private String defaultDateFrom;

    /** 默认查询日止（yyyy-MM-dd，当日）。 */
    @Schema(description = "默认查询日止")
    private String defaultDateTo;

    /** 默认分页大小。 */
    @Schema(description = "默认分页大小")
    private Integer defaultPageSize;

    /** 默认汇总视角（DEPARTMENT）。 */
    @Schema(description = "默认汇总视角")
    private String defaultSummaryView;

    /** 门诊挂号科室下拉。 */
    @Schema(description = "挂号科室选项")
    private List<OptionDto> organizationOptions;

    /** 挂号医生下拉（全院医生岗，可前端按科室级联）。 */
    @Schema(description = "挂号医生选项")
    private List<OptionDto> practitionerOptions;

    /** 号源类型下拉。 */
    @Schema(description = "号源类型选项")
    private List<OptionDto> serviceTypeOptions;

    /** 汇总视角下拉（科室/医生）。 */
    @Schema(description = "汇总视角选项")
    private List<OptionDto> summaryViewOptions;

    /**
     * 就诊状态下拉（已挂号/在诊/退号等；value=EncounterStatus code，CANCELLED 展示「退号」）。
     */
    @Schema(description = "就诊状态选项")
    private List<OptionDto> statusOptions;
}

