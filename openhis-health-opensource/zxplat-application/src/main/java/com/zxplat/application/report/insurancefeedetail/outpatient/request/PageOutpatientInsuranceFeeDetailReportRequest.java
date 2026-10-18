/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.insurancefeedetail.outpatient.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 门诊医保费用明细分页请求（IF-RPT-OIF-02）。
 */
@Data
@Schema(description = "门诊医保费用明细分页请求")
public class PageOutpatientInsuranceFeeDetailReportRequest {

    /** 结算时间窗起（yyyy-MM-dd HH:mm:ss）。 */
    @Schema(description = "结算时间窗起")
    private String dateFrom;

    /** 结算时间窗止（yyyy-MM-dd HH:mm:ss）。 */
    @Schema(description = "结算时间窗止")
    private String dateTo;

    /**
     * 省/市费用类别码多选；与 {@link #workInjuryOnly} 互斥。
     */
    @Schema(description = "省/市费用类别码")
    private List<String> contractCategoryCodes;

    /** 为 true 时仅查工伤结算。 */
    @Schema(description = "仅工伤")
    private Boolean workInjuryOnly;

    /** 职工/居民分组：ALL / EMPLOYEE / RESIDENT。 */
    @Schema(description = "职工居民分组")
    private String insutypeGroup;

    /** 挂号科室主键。 */
    @Schema(description = "挂号科室主键")
    private Long organizationId;

    /** 结算 ID 模糊。 */
    @Schema(description = "结算ID关键字")
    private String setlIdKeyword;

    /** 就医登记号模糊。 */
    @Schema(description = "就医登记号关键字")
    private String mdtrtIdKeyword;

    /** 门诊号模糊。 */
    @Schema(description = "门诊号关键字")
    private String busNoKeyword;

    /** 患者姓名模糊。 */
    @Schema(description = "患者姓名关键字")
    private String patientNameKeyword;

    /** 页码（1-based）。 */
    @Schema(description = "页码")
    private Integer pageNo;

    /** 页大小。 */
    @Schema(description = "页大小")
    private Integer pageSize;
}
