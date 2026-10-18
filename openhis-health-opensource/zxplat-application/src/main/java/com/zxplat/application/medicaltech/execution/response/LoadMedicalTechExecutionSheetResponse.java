/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.execution.response;

import com.zxplat.application.medicaltech.execution.dto.MedicalTechBoundSupplyItemDto;
import com.zxplat.application.medicaltech.execution.dto.MedicalTechExecutingOrgOptionDto;
import com.zxplat.application.medicaltech.execution.dto.MedicalTechExecutionLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 加载医技执行单响应（AS-MT-EXE-001 IF-MT-EXE-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "加载医技执行单响应")
public class LoadMedicalTechExecutionSheetResponse {

    /** 本站可见诊疗行。 */
    @Schema(description = "诊疗行")
    private List<MedicalTechExecutionLineDto> serviceLines;

    /** 本站可见耗材行。 */
    @Schema(description = "耗材行")
    private List<MedicalTechExecutionLineDto> deviceLines;

    /**
     * 开立带出耗材预览（住院为主；只读，不参与执行/取消）。
     *
     * <p>来自诊疗 ACTIVITY 绑定模板；与 {@link #deviceLines} 并存，前端合并展示。</p>
     */
    @Schema(description = "开立带出耗材预览（只读）")
    private List<MedicalTechBoundSupplyItemDto> boundSupplyItems;

    /**
     * 共看组项目类型筛选项（登录科落在共看组时非空；否则空）。
     *
     * <p>如 CT/DR：按执行科室区分项目类型；前端仅筛项目列表，不筛患者队列。</p>
     */
    @Schema(description = "共看组项目类型筛选项（CT/DR 等）")
    private List<MedicalTechExecutingOrgOptionDto> sharedExecutingOrgFilterOptions;
}
