package com.openhis.whale.report.application.external.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * D 类外系统报告解析请求。
 */
@Getter
@Setter
public class ReportExternalDocumentRequest {

    /** 文档编码，如 {@code lis_report}。 */
    @NotBlank
    private String documentCode;

    /** 业务参数，如 {@code encounterId}、{@code reportId}。 */
    private Map<String, Object> params;
}
