package com.openhis.whale.report.application.external.service;

import com.openhis.whale.report.application.external.response.ReportExternalDocumentDto;
import com.openhis.whale.report.application.external.response.ReportExternalSourceDto;
import com.openhis.whale.report.application.external.request.ReportExternalDocumentRequest;
import com.openhis.whale.report.domain.port.external.ReportExternalDocumentPort;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * D 类外系统报告查看 → {@code POST /api/app/reportExternalView/*}
 */
@Service
@Validated
public class ReportExternalViewAppService {

    private final ReportExternalDocumentPort externalDocumentPort;

    public ReportExternalViewAppService(
            @Autowired(required = false) ReportExternalDocumentPort externalDocumentPort) {
        this.externalDocumentPort = externalDocumentPort;
    }

    /**
     * 外系统报告来源列表 → {@code POST /api/app/reportExternalView/listSources}
     *
     * @return 固定 LIS/PACS 等文档类型
     */
    public List<ReportExternalSourceDto> listSources() {
        List<ReportExternalSourceDto> sources = new ArrayList<>();
        sources.add(source("lis_report", "检验报告 PDF", "D-View"));
        sources.add(source("pacs_report", "检查报告 PDF", "D-View"));
        sources.add(source("pathology_report", "病理报告 PDF", "D-View"));
        sources.add(source("hc_report", "体检报告", "D-View"));
        return sources;
    }

    /**
     * 解析外系统报告文档 → {@code POST /api/app/reportExternalView/resolve}
     *
     * @param request 文档编码与参数
     * @return 可用性、URL 与回显参数
     */
    public ReportExternalDocumentDto resolve(@Valid ReportExternalDocumentRequest request) {
        ReportExternalDocumentDto dto = new ReportExternalDocumentDto();
        dto.setDocumentCode(request.getDocumentCode());
        if (externalDocumentPort == null) {
            dto.setAvailable(false);
            dto.setTitle(request.getDocumentCode());
            dto.setMessage("ReportExternalDocumentPort 未装配");
            return dto;
        }
        Map<String, Object> params = Map.of();
        if (request.getParams() != null) {
            params = new LinkedHashMap<>(request.getParams());
        }
        Map<String, Object> loaded = externalDocumentPort.resolve(
                request.getDocumentCode(),
                params);
        dto.setAvailable(asBoolean(loaded.get("available")));
        dto.setTitle(asString(loaded.get("title")));
        dto.setContentType(asString(loaded.get("contentType")));
        dto.setDocumentUrl(asString(loaded.get("documentUrl")));
        dto.setMessage(asString(loaded.get("message")));
        Object echo = loaded.get("paramsEcho");
        if (echo instanceof Map<?, ?> echoMap) {
            Map<String, Object> paramsEcho = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : echoMap.entrySet()) {
                if (entry.getKey() != null) {
                    paramsEcho.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            dto.setParamsEcho(paramsEcho);
        }
        return dto;
    }

    private static ReportExternalSourceDto source(String code, String title, String scheme) {
        ReportExternalSourceDto dto = new ReportExternalSourceDto();
        dto.setDocumentCode(code);
        dto.setTitle(title);
        dto.setScheme(scheme);
        return dto;
    }

    private static Boolean asBoolean(Object raw) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return false;
    }

    private static String asString(Object raw) {
        if (raw != null) {
            return String.valueOf(raw);
        }
        return "";
    }
}
