/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.execution.request;

import com.openhis.health.application.shared.pagination.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 分页查询医技患者队列请求（AS-MT-EXE-001 IF-MT-EXE-01）。
 *
 * <p>关键字与就诊开始时间窗继承 {@link AbstractHealthPageRequest}
 * （{@code keyword}/{@code startDate}/{@code endDate}）。
 * 时间窗**仅门诊**使用；住院由服务层忽略。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "分页查询医技患者队列请求")
public class PageMedicalTechPatientQueueRequest extends AbstractHealthPageRequest {

    /** 就诊类别 scope：{@code OUTPATIENT} / {@code INPATIENT}。 */
    @Schema(description = "就诊类别 scope", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encounterScope;

    /** true=仅本站仍有待发耗材的就诊。 */
    @Schema(description = "仅待发耗材")
    private Boolean pendingDeviceOnly;

    /**
     * 住院在院态筛选（仅住院生效）：{@code IN_HOSPITAL}（默认）/ {@code NOT_IN_HOSPITAL}（已出院）。
     *
     * <p>空或非法时按在院处理；门诊忽略。</p>
     */
    @Schema(description = "住院在院态筛选 IN_HOSPITAL/NOT_IN_HOSPITAL")
    private String inpatientStayFilter;
}
