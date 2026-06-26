package com.openhis.whale.report.application.report.response;

import lombok.Data;

/** 筛选器下拉选项。 */
@Data
public class ReportFilterSelectOptionDto {

    /** 展示文案。 */
    private String label;
    /** 提交值。 */
    private String value;
}
