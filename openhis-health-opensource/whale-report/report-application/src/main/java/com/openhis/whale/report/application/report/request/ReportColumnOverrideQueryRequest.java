package com.openhis.whale.report.application.report.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 查询或重置列定制请求。 */
@Data
public class ReportColumnOverrideQueryRequest {

    /** 报表编码。 */
    @NotBlank
    @JsonAlias("ReportCode")
    private String reportCode;
}
