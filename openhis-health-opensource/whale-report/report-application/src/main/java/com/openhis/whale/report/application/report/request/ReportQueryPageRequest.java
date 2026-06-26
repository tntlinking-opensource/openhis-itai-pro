package com.openhis.whale.report.application.report.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/** C 类报表分页查询/导出请求。 */
@Data
public class ReportQueryPageRequest {

    /** 报表编码（必填）。 */
    @NotBlank
    @JsonAlias("ReportCode")
    private String reportCode;

    /** 页码，从 1 开始。 */
    @Min(1)
    private int pageNo = 1;

    /** 每页条数，最大 500。 */
    @Min(1)
    @Max(500)
    private int pageSize = 20;

    /** 开始日期（yyyy-MM-dd，与 filterJson 中 dateFrom 对应）。 */
    private String dateFrom;
    /** 结束日期（yyyy-MM-dd）。 */
    private String dateTo;
    /** 关键词模糊筛选。 */
    private String keyword;
    /** 扩展命名参数，透传 SQL / Loader。 */
    private Map<String, Object> params;

    /** 设计器试查时为 true，跳过发布校验。 */
    private Boolean designPreview;
}
