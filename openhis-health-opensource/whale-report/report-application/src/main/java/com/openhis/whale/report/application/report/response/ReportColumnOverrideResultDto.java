package com.openhis.whale.report.application.report.response;

import lombok.Data;

import java.util.List;

/** 列定制查询/保存结果。 */
@Data
public class ReportColumnOverrideResultDto {

    /** 报表编码。 */
    private String reportCode;
    /** 当前用户生效列（默认 + override merge 后）。 */
    private List<ReportColumnDto> columns;
    /** 是否存在用户级 override。 */
    private Boolean customized;
}
