package com.openhis.whale.report.application.report.response;

import lombok.Data;

import java.util.List;

/** 报表表头或表底设计（设计器结构化出参，替代 JSON 字符串）。 */
@Data
public class ReportHeaderConfigDto {

    /** 主标题。 */
    private String title;
    /** 副标题。 */
    private String subtitle;
    /** 动态行列表。 */
    private List<ReportHeaderLineDto> lines;
}
