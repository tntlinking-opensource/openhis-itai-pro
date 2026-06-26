package com.openhis.whale.report.application.report.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 复制已有报表定义为新 SQL 报表请求。 */
@Data
public class CopyReportDefinitionRequest {

    /** 源报表定义主键。 */
    @NotNull
    private Long sourceId;

    /** 新报表编码，当前租户内唯一。 */
    @NotBlank
    @JsonAlias("ReportCode")
    private String reportCode;

    /** 新展示名称。 */
    @NotBlank
    private String name;

    /** 新备注（可选）。 */
    private String remark;
}
