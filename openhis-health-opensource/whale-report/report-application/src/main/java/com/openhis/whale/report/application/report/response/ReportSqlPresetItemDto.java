package com.openhis.whale.report.application.report.response;

import lombok.Data;

/** 平台 SQL 预制报表目录项。 */
@Data
public class ReportSqlPresetItemDto {

    /** 预制报表编码（tenant_id=0）。 */
    private String reportCode;
    /** 展示名称。 */
    private String name;
    /** 备注（含优先级标记如「· P2 ·」）。 */
    private String remark;
    /** 解析后的优先级：{@code P2} / {@code P3} / {@code OTHER}。 */
    private String priority;
}
