package com.openhis.whale.report.application.report.response;

import lombok.Data;

import java.util.List;

/** 报表中心/设计器筛选字段定义。 */
@Data
public class ReportFilterFieldDto {

    /** 参数字段名（对应 queryPage.params 或顶层 dateFrom 等）。 */
    private String field;
    /** 表单展示标签。 */
    private String label;
    /** 控件类型：date / text / select / daterange / month。 */
    private String type;
    /** select 类型时的选项列表。 */
    private List<ReportFilterSelectOptionDto> options;
}
