package com.openhis.whale.report.application.query.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class ReportQueryPageRequest {

    @NotBlank
    private String reportCode;

    @Min(1)
    private int pageNo = 1;

    @Min(1)
    @Max(500)
    private int pageSize = 20;

    private String dateFrom;
    private String dateTo;
    private String keyword;
    private Map<String, Object> params;

    /** 设计器试查时为 true，跳过发布校验 */
    private Boolean designPreview;
}
