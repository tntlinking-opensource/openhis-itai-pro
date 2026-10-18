/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.request;

import java.time.LocalDate;
import java.util.List;

import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 住院登记待办列表分页请求（IF-IP-REG-01）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "住院登记待办列表分页请求")
public class PageRegistrationQueueRequest extends AbstractHealthPageRequest {

    /** 登记状态筛选（多选）（业务字段）。 */
    @Schema(description = "登记状态筛选（多选）：PLANNED 待登记 / WAIT_ADMISSION 待入科 / ADMITTED 已入科等")
    private List<String> registrationListStatuses;

    /** 登记状态筛选（单选，兼容旧入参；与 registrationListStatuses 二选一）。 */
    @Schema(description = "登记状态筛选（单选，兼容旧入参）")
    private String registrationListStatus;

    /** 身份证号精确筛选。 */
    @Schema(description = "身份证号精确筛选")
    private String idCard;

    /** 住院科室主键；按就诊 organization_id 等值筛选。 */
    @Schema(description = "住院科室主键")
    private Long organizationId;

    /** 入院病区主键；按活动病区/下属床位轨迹筛选。 */
    @Schema(description = "入院病区主键")
    private Long wardId;

    /** 入院日起（含自然日）；与 admissionDateTo 组成闭区间，过滤 startTime。 */
    @Schema(description = "入院日起（含），yyyy-MM-dd")
    private LocalDate admissionDateFrom;

    /** 入院日止（含自然日）；语义同 admissionDateFrom。 */
    @Schema(description = "入院日止（含），yyyy-MM-dd")
    private LocalDate admissionDateTo;
}
