package com.openhis.whale.report.application.report.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.openhis.whale.report.application.report.response.ReportColumnDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 保存当前用户列定制请求。 */
@Data
public class SaveReportColumnOverrideRequest {

    /** 报表编码。 */
    @NotBlank
    @JsonAlias("ReportCode")
    private String reportCode;

    /** 列 visible/sortOrder/width 配置；field 须与默认列一致。 */
    @NotEmpty
    private List<ReportColumnDto> columns;
}
