package com.openhis.whale.report.application.report.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 将平台预制 SQL 设计应用到当前草稿报表请求。 */
@Data
public class ApplySqlPresetRequest {

    /** 目标报表定义主键（须为 SQL 类型草稿）。 */
    @NotNull
    private Long id;

    /** 平台预制报表 report_code（tenant_id=0）。 */
    @NotBlank
    private String presetReportCode;
}
