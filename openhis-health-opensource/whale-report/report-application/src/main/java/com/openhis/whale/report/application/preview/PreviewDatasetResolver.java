package com.openhis.whale.report.application.preview;

import com.openhis.whale.report.application.design.request.PreviewRequest;
import com.openhis.whale.report.domain.constants.ReportPackConstants;
import com.openhis.whale.report.domain.port.print.PrintDataPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设计器预览数据集解析：样例 / Port 实数 / 按 pack 回退样例。
 */
@Service
public class PreviewDatasetResolver {

    private final PrintDataPort printDataPort;

    public PreviewDatasetResolver(@Autowired(required = false) PrintDataPort printDataPort) {
        this.printDataPort = printDataPort;
    }

    /**
     * @param request  预览请求（含 useSampleData 与 params）
     * @param packCode 元素包编码
     * @return 供 bindPath 绑定的 root Map
     */
    public Map<String, Object> resolve(PreviewRequest request, String packCode) {
        if (shouldUseSampleData(request, packCode)) {
            return PreviewSampleRegistry.sampleForPack(packCode);
        }
        if (isStubPack(packCode)) {
            return resolveStubPackDataset(request, packCode);
        }
        if (printDataPort == null || !PreviewDatasetParamSupport.hasRealParams(packCode, request.getParams())) {
            return PreviewSampleRegistry.sampleForPack(packCode);
        }
        Map<String, Object> loaded = printDataPort.load(packCode, request.getParams());
        if (loaded == null || loaded.isEmpty()) {
            return PreviewSampleRegistry.sampleForPack(packCode);
        }
        return loaded;
    }

    /**
     * 设计器预览优先样例：显式 useSampleData，或 inline layout 且无 Port 实参。
     */
    private static boolean shouldUseSampleData(PreviewRequest request, String packCode) {
        if (request == null) {
            return true;
        }
        if (Boolean.TRUE.equals(request.getUseSampleData())) {
            return true;
        }
        if (Boolean.FALSE.equals(request.getUseSampleData())) {
            return false;
        }
        return StringUtils.hasText(request.getLayoutJson())
                && !PreviewDatasetParamSupport.hasRealParams(packCode, request.getParams());
    }

    private static boolean isStubPack(String packCode) {
        return ReportPackConstants.PRINT_PAGE_STUB.equals(packCode)
                || ReportPackConstants.PRINT_ROLL_STUB.equals(packCode)
                || ReportPackConstants.PRINT_PAGE_OVERLAY_STUB.equals(packCode);
    }

    private Map<String, Object> resolveStubPackDataset(PreviewRequest request, String packCode) {
        Map<String, Object> params = enrichStubParams(request);
        if (printDataPort == null || !PreviewDatasetParamSupport.hasRealParams(packCode, params)) {
            return PreviewSampleRegistry.sampleForPack(packCode);
        }
        Map<String, Object> loaded = printDataPort.load(packCode, params);
        if (loaded == null || loaded.isEmpty()) {
            return PreviewSampleRegistry.sampleForPack(packCode);
        }
        return loaded;
    }

    private static Map<String, Object> enrichStubParams(PreviewRequest request) {
        Map<String, Object> params = request.getParams() != null
                ? new LinkedHashMap<>(request.getParams())
                : new LinkedHashMap<>();
        if (!StringUtils.hasText((String) params.get("templateCode"))
                && StringUtils.hasText(request.getTemplateCode())) {
            params.put("templateCode", request.getTemplateCode().trim());
        }
        return params;
    }
}
