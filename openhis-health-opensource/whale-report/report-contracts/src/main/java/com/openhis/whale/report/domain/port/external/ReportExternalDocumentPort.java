package com.openhis.whale.report.domain.port.external;

import java.util.Map;

/**
 * D 类外系统报告查看 Port（LIS/PACS/病理等 PDF 由院点 Adapter 提供）。
 */
public interface ReportExternalDocumentPort {

    /**
     * @param documentCode 文档编码，如 {@code lis_report}、{@code pacs_report}
     * @param params       常用键：{@code encounterId}、{@code reportId}、{@code patientId}
     * @return 元数据：{@code available}、{@code title}、{@code contentType}、{@code documentUrl}、{@code message}
     */
    Map<String, Object> resolve(String documentCode, Map<String, Object> params);
}
