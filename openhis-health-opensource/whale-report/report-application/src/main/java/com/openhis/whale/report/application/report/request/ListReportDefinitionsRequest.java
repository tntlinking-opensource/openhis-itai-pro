package com.openhis.whale.report.application.report.request;

import lombok.Data;

/** 报表定义列表筛选请求。 */
@Data
public class ListReportDefinitionsRequest {

    /** 发布状态筛选：{@code draft} / {@code published}；空表示全部。 */
    private String status;

    /** 名称或编码关键词（大小写不敏感包含匹配）。 */
    private String keyword;
}
