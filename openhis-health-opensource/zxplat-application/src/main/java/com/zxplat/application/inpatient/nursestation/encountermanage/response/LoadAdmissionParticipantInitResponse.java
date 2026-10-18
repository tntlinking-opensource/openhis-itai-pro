/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.encountermanage.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 分床/入科责任医护下拉初始化响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "分床责任医护下拉初始化响应")
public class LoadAdmissionParticipantInitResponse {

    @Schema(description = "住院医师/主治/主任医生候选")
    /** 住院医师/主治/主任医生候选。 */
    private List<OptionDto> doctorOptions;

    @Schema(description = "责任护士候选")
    /** 责任护士候选。 */
    private List<OptionDto> primaryNurseOptions;

    @Schema(description = "患者病情下拉（术语 adm_cond_code）")
    /** 患者病情下拉（术语 adm_cond_code）。 */
    private List<OptionDto> admCondCodeOptions;

    /**
     * 院点策略默认主任医生主键（字符串雪花）；未配置时 null，前端勿覆盖已选手填。
     */
    @Schema(description = "默认主任医生主键（院点策略；可空）")
    private String defaultChiefDoctorId;

    /**
     * 院点策略默认主治医生主键（字符串雪花）；未配置时 null，前端勿覆盖已选手填。
     */
    @Schema(description = "默认主治医生主键（院点策略；可空）")
    private String defaultAttendingDoctorId;

    /**
     * 院点策略默认住院医生主键（字符串雪花）；未配置时 null，前端勿覆盖已选手填。
     */
    @Schema(description = "默认住院医生主键（院点策略；可空）")
    private String defaultInpatientDoctorId;
}
