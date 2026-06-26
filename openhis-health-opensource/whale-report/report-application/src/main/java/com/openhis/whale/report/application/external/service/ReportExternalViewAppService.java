package com.openhis.whale.report.application.external.service;

import com.openhis.whale.report.application.external.request.ReportExternalDocumentRequest;
import com.openhis.whale.report.application.external.response.ReportExternalDocumentDto;
import com.openhis.whale.report.application.external.response.ReportExternalSourceDto;
import com.openhis.whale.report.contracts.reader.ReportExternalDocumentReader;
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

    private final ReportExternalDocumentReader externalDocumentReader;

    public ReportExternalViewAppService(
            @Autowired(required = false) ReportExternalDocumentReader externalDocumentReader) {
        this.externalDocumentReader = externalDocumentReader;
    }

    /**
     * 列出外系统报告来源目录（出厂静态列表）。
     *
     * @return 文档来源项
     */
    public List<ReportExternalSourceDto> listSources() {
        // Gate：无入参，返回出厂文档来源目录
        List<ReportExternalSourceDto> sources = new ArrayList<>();
        sources.add(source("lis_report", "检验报告 PDF", "D-View"));
        sources.add(source("pacs_report", "检查报告 PDF", "D-View"));
        sources.add(source("pathology_report", "病理报告 PDF", "D-View"));
        sources.add(source("hc_report", "体检报告", "D-View"));
        return sources;
    }

    /**
     * 解析外系统报告访问元数据（URL、可用性等）。
     *
     * @param request 文档编码与扩展参数
     * @return 解析结果；Port 未装配时 available=false
     */
    public ReportExternalDocumentDto resolve(@Valid ReportExternalDocumentRequest request) {
        // Gate：documentCode 必填，由 @Valid 校验
        ReportExternalDocumentDto dto = new ReportExternalDocumentDto();
        dto.setDocumentCode(request.getDocumentCode());
        if (externalDocumentReader == null) {
            // adapter 未引入时降级，避免启动失败
            dto.setAvailable(false);
            dto.setTitle(request.getDocumentCode());
            dto.setMessage("ReportExternalDocumentReader 未装配");
            return dto;
        }
        // Pre：整理 params，空则传空 Map
        Map<String, Object> params = Map.of();
        if (request.getParams() != null) {
            params = new LinkedHashMap<>(request.getParams());
        }
        // Cmd：经 Reader/Writer/Client 解析外系统报告元数据
        Map<String, Object> loaded = externalDocumentReader.resolve(request.getDocumentCode(), params);
        // Asm：Map 转 DTO
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
