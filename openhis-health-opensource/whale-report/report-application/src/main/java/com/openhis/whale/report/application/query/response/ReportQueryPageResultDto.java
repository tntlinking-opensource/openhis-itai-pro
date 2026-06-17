package com.openhis.whale.report.application.query.response;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ReportQueryPageResultDto {

    private String reportCode;
    private String reportName;
    private List<ReportColumnDto> columns;
    private List<Map<String, Object>> rows;
    private long totalCount;
    private int pageNo;
    private int pageSize;
    /** 汇总区（如日结合计），可选。 */
    private Map<String, Object> summary;
    /** 表头（设计器配置）。 */
    private Map<String, Object> header;
    /** 表底（设计器配置）。 */
    private Map<String, Object> footer;
}
