package com.openhis.whale.report.application.report.response;

import lombok.Data;

import java.util.List;

/** 报表表底设计（notes + 多行文本）。 */
@Data
public class ReportFooterConfigDto {

    /** 表底备注区文案。 */
    private String notes;
    /** 表底附加行（每元素一行）。 */
    private List<String> lines;
}
