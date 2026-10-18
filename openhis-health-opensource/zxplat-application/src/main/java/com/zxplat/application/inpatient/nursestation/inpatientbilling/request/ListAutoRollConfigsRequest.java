/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.request;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * IF-IP-ARG-02：自动计费配置列表请求（含护理级别只读区）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "自动计费配置列表请求")
public class ListAutoRollConfigsRequest {

    /** 住院就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "住院就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /**
     * 业务日（展示透传；护理行不按此裁剪）。
     *
     * <p>护理只读列表始终为全住院期已校对/已停嘱行；按日筛已滚费用在 IF-IP-ARG-09 的 dateFrom/dateTo。</p>
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "业务日（不裁剪护理行；展开费用按日过滤）")
    private LocalDate billDate;

    /**
     * 是否装载计费人护士下拉。
     *
     * <p>空或 true：返回 {@code nurseOptions}；false：跳过全量护士查询（刷新/按日查询减负，前端可缓存首屏结果）。</p>
     */
    @Schema(description = "是否装载计费人护士下拉；false 跳过")
    private Boolean includeNurseOptions;
}
