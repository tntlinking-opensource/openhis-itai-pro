/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.execution.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 加载医技执行单请求（AS-MT-EXE-001 IF-MT-EXE-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "加载医技执行单请求")
public class LoadMedicalTechExecutionSheetRequest {

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 就诊类别 scope：与队列一致。 */
    @Schema(description = "就诊类别 scope", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encounterScope;

    /** 诊疗类目码过滤；空表示不过滤。 */
    @Schema(description = "诊疗类目码过滤")
    private String serviceCategoryCode;

    /**
     * 执行态分桶（与队列同码，BR-MT-EXE-023）：{@code PENDING}/{@code EXECUTED}。
     *
     * <p>同时收窄右侧诊疗行；门诊另收窄耗材行。空或非法码不按执行态收窄。</p>
     */
    @Schema(description = "执行态 PENDING/EXECUTED；空=不按执行态收窄右侧项目")
    private String executionQueueFilter;
}
