package com.openhis.whale.report.application.report.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 更新报表基本信息（名称、备注）请求。 */
@Data
public class UpdateReportDefinitionRequest {

    /** 报表定义主键 {@code rpt_definition.id}。 */
    @NotNull
    private Long id;

    /** 新展示名称（可选）。 */
    private String name;

    /** 新备注（可选；传空串可清空）。 */
    private String remark;
}
