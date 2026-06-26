package com.openhis.whale.report.application.external.response;

import lombok.Getter;
import lombok.Setter;

/**
 * D 类外系统报告来源项。
 */
@Getter
@Setter
public class ReportExternalSourceDto {

    /** 文档编码。 */
    private String documentCode;

    /** 展示标题。 */
    private String title;

    /** 查看方案，如 {@code D-View}。 */
    private String scheme;
}
