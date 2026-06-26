package com.openhis.whale.report.application.report.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 新建 SQL 报表定义请求。 */
@Data
public class CreateReportDefinitionRequest {

    /** 报表编码，当前租户内唯一。 */
    @NotBlank
    @JsonAlias("ReportCode")
    private String reportCode;

    /** 展示名称。 */
    @NotBlank
    private String name;

    /** 备注（可选）。 */
    private String remark;

    /** 可选：从平台预制 SQL 报表（tenant_id=0）复制设计内容。 */
    private String presetReportCode;
}
