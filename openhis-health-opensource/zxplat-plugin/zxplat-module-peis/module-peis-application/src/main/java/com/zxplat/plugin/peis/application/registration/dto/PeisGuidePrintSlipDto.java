/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 体检指引单打印 slip（与 peis-guide-print 模板 PrintData 契约一致）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体检指引单打印 slip")
public class PeisGuidePrintSlipDto {

    @Schema(description = "机构名称")
    private String hospitalName;

    @Schema(description = "患者姓名")
    private String patientName;

    @Schema(description = "性别展示")
    private String genderEnumText;

    @Schema(description = "年龄展示")
    private String ageText;

    @Schema(description = "体检流水号")
    private String busNo;

    @Schema(description = "套餐名称")
    private String packageName;

    @Schema(description = "登记时间展示")
    private String registeredAtText;

    @Schema(description = "就诊ID")
    private Long encounterId;

    @Schema(description = "患者ID")
    private Long patientId;

    @Schema(description = "打印场景码")
    private String scenarioCode;

    @Schema(description = "指引项目行")
    private List<GuideLineDto> guideLines = new ArrayList<>();

    /**
     * 指引单项目行（与模板 guideLines 列 field 一致）。
     */
    @Data
    @Accessors(chain = true)
    @Schema(description = "指引单项目行")
    public static class GuideLineDto {

        @Schema(description = "序号")
        private String seqText;

        @Schema(description = "执行科室")
        private String deptName;

        @Schema(description = "项目名称")
        private String itemName;

        @Schema(description = "地点/诊室")
        private String locationText;
    }
}
