package com.openhis.whale.report.application.report.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 按主键获取报表定义请求。 */
@Data
public class GetReportDefinitionRequest {

    /** 报表定义主键 {@code rpt_definition.id}。 */
    @NotNull
    private Long id;
}
