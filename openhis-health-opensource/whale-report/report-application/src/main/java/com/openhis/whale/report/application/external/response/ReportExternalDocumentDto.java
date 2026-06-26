package com.openhis.whale.report.application.external.response;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * D 类外系统报告解析结果。
 */
@Getter
@Setter
public class ReportExternalDocumentDto {

    /** 文档编码。 */
    private String documentCode;

    /** 展示标题。 */
    private String title;

    /** 是否可查看。 */
    private Boolean available;

    /** MIME 类型，如 {@code application/pdf}。 */
    private String contentType;

    /** 文档 URL 或内嵌地址。 */
    private String documentUrl;

    /** 不可用时的提示信息。 */
    private String message;

    /** 回显请求参数，便于联调。 */
    private Map<String, Object> paramsEcho;
}
