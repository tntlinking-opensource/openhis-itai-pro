/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.charging.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 加载医技计费初始化响应（AS-MT-CHG-001 IF-MT-CHG-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "加载医技计费初始化响应")
public class LoadMedicalTechChargingInitResponse {

    /** 默认耗材房主键（首个选项）。 */
    @Schema(description = "默认耗材房主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long defaultLocationId;

    /** 登录 TECH 下属耗材房选项。 */
    @Schema(description = "耗材房选项")
    private List<OptionDto> locationOptions;

    /** 就诊主键回显。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;
}
