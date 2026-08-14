/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.registrationdetail.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 门诊挂号明细报表明细分页请求（IF-RPT-ORG-03）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "门诊挂号明细报表明细分页请求")
public class PageOutpatientRegistrationDetailReportDetailRequest extends AbstractHealthPageRequest {

    /** 挂号业务日起（yyyy-MM-dd，闭区间）。 */
    @Schema(description = "挂号业务日起（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateFrom;

    /** 挂号业务日止（yyyy-MM-dd，闭区间）。 */
    @Schema(description = "挂号业务日止（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateTo;

    /** 挂号科室主键；空表示不按单科收窄。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "挂号科室主键")
    private Long organizationId;

    /** 接诊医生主键（活动 ADMITTER）；空表示不按接诊医生收窄。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "接诊医生主键（活动 ADMITTER）")
    private Long practitionerId;

    /**
     * 患者姓名/门诊号关键字使用基类 {@link AbstractHealthPageRequest#getKeyword()}，
     * 禁止在本类再声明同名字段（会与 {@code AbstractPageQuery#setKeyword} 冲突）。
     */
}

