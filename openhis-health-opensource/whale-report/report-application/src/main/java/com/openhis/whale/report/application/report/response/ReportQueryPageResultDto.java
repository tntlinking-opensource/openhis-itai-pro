package com.openhis.whale.report.application.report.response;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** C 类报表分页查询结果（列、行、表头表底均为结构化字段）。 */
@Data
public class ReportQueryPageResultDto {

    /** 报表编码。 */
    private String reportCode;
    /** 报表名称。 */
    private String reportName;
    /** 有效列（已 merge 列定制）。 */
    private List<ReportColumnDto> columns;
    /** 数据行，每行 field→value。 */
    private List<Map<String, Object>> rows;
    /** 总行数（内存分页前的全量计数）。 */
    private long totalCount;
    /** 当前页码（从 1 开始）。 */
    private int pageNo;
    /** 每页条数。 */
    private int pageSize;
    /** 汇总区（如日结合计），可选。 */
    private Map<String, Object> summary;
    /** 表头（设计器配置解析后，非 JSON 串）。 */
    private Map<String, Object> header;
    /** 表底（设计器配置解析后，非 JSON 串）。 */
    private Map<String, Object> footer;
}
