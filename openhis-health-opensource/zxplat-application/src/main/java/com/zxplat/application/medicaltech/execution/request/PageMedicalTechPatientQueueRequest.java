/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.execution.request;

import com.zxplat.application.shared.pagination.AbstractHealthPageRequest;
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

    /** true=在本站诊疗有活之上再收窄「仍有待发耗材」；仅耗材无诊疗不进队。 */
    @Schema(description = "仅待发耗材（须同时有本站诊疗项目）")
    private Boolean pendingDeviceOnly;

    /**
     * 队列执行态筛选：{@code PENDING}（待执行）/ {@code EXECUTED}（已执行）。
     *
     * <p>空或非法时不按执行态收窄。门诊按本站诊疗项目+耗材整单判定；住院仅按诊疗项目（不看耗材）。
     * 对齐 BR-MT-EXE-023。</p>
     */
    @Schema(description = "队列执行态 PENDING/EXECUTED")
    private String executionQueueFilter;

    /**
     * 住院在院态筛选（仅住院生效）：{@code IN_HOSPITAL}（默认）/ {@code NOT_IN_HOSPITAL}（已出院）。
     *
     * <p>空或非法时按在院处理；门诊忽略。</p>
     */
    @Schema(description = "住院在院态筛选 IN_HOSPITAL/NOT_IN_HOSPITAL")
    private String inpatientStayFilter;
}
