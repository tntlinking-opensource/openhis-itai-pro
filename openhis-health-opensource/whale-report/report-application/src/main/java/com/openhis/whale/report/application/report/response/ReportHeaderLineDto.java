package com.openhis.whale.report.application.report.response;

import lombok.Data;

/** 表头/表底动态行：{@code text} 单行或 {@code split} 左右分栏。 */
@Data
public class ReportHeaderLineDto {

    /** 行类型：{@code text} / {@code split}。 */
    private String type;
    /** 单行文本（type=text）。 */
    private String text;
    /** 对齐：left / center / right。 */
    private String align;
    /** 分栏左文案（type=split）。 */
    private String left;
    /** 分栏右文案（type=split）。 */
    private String right;
}
