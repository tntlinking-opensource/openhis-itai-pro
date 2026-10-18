/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.practitionerworkload.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 科室/医生工作量统计页初始化响应（IF-RPT-WKL-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "科室医生工作量统计页初始化响应")
public class LoadPractitionerWorkloadPageInitResponse {

    /** 就诊范围选项。 */
    @Schema(description = "就诊范围选项")
    private List<OptionDto> encounterScopeOptions;

    /** 默认就诊范围。 */
    @Schema(description = "默认就诊范围")
    private String defaultEncounterScope;

    /** 科室选项。 */
    @Schema(description = "科室选项")
    private List<OptionDto> organizationOptions;

    /** 医生选项。 */
    @Schema(description = "医生选项")
    private List<OptionDto> practitionerOptions;

    /** 默认查询日起 yyyy-MM-dd。 */
    @Schema(description = "默认查询日起 yyyy-MM-dd")
    private String defaultDateFrom;

    /** 默认查询日止 yyyy-MM-dd。 */
    @Schema(description = "默认查询日止 yyyy-MM-dd")
    private String defaultDateTo;

    /** 默认激活 Tab。 */
    @Schema(description = "默认激活 Tab")
    private String defaultActiveTab;

    /**
     * 是否已配置有效折扣内容（≥1 条未删 ACTIVE 配置）。
     * 配置本身即开关。true 时明细/汇总展示优惠金额。
     */
    @Schema(description = "是否已配置有效折扣内容")
    private Boolean discountFeatureEnabled;
}
