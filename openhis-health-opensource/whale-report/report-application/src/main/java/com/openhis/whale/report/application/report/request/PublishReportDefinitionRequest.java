package com.openhis.whale.report.application.report.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 发布或撤回报表、软删定义等单主键操作请求。 */
@Data
public class PublishReportDefinitionRequest {

    /** 报表定义主键 {@code rpt_definition.id}。 */
    @NotNull
    private Long id;
}
