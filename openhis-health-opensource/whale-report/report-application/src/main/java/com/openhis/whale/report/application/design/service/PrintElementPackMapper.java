package com.openhis.whale.report.application.design.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.report.application.design.response.ElementPackDto;
import com.openhis.whale.report.application.design.response.PrintElementDefDto;
import com.openhis.whale.report.application.preview.PreviewSampleRegistry;
import com.openhis.whale.report.domain.constants.ReportPackConstants;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import com.openhis.whale.report.domain.service.PrintElementDefManager;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Component
public class PrintElementPackMapper {

    private final ObjectMapper objectMapper;
    private final PrintElementDefManager elementDefManager;

    public PrintElementPackMapper(ObjectMapper objectMapper, PrintElementDefManager elementDefManager) {
        this.objectMapper = objectMapper;
        this.elementDefManager = elementDefManager;
    }

    public ElementPackDto toPackDto(String packCode, Long tenantId, String layoutMode) {
        String effectivePack = effectivePackCode(packCode);
        List<PrintElementDef> defs = elementDefManager.listPackElements(effectivePack, tenantId, layoutMode);
        ElementPackDto dto = new ElementPackDto();
        dto.setPackCode(effectivePack);
        dto.setPipeline(resolvePipeline(effectivePack));
        dto.setElements(defs.stream().map(this::toElementDto).toList());
        dto.setSampleData(resolveSampleData(effectivePack));
        return dto;
    }

    public Map<String, PrintElementDef> loadPackIndex(String packCode, Long tenantId) {
        return elementDefManager.loadPackIndex(effectivePackCode(packCode), tenantId);
    }

    private static String effectivePackCode(String packCode) {
        if (StringUtils.hasText(packCode)) {
            return packCode.trim();
        }
        return ReportPackConstants.DEFAULT_PACK;
    }

    private PrintElementDefDto toElementDto(PrintElementDef def) {
        PrintElementDefDto dto = new PrintElementDefDto();
        dto.setElementCode(def.getElementCode());
        dto.setLabel(def.getLabel());
        dto.setRenderType(def.getRenderType());
        dto.setBindPath(def.getBindPath());
        dto.setLayoutMode(def.getLayoutMode());
        dto.setRequired(def.getRequired());
        dto.setDefaultLabel(def.getDefaultLabel());
        dto.setDefaultStyle(readJsonMap(def.getDefaultStyleJson()));
        dto.setKvItems(readJsonList(def.getKvItemsJson()));
        dto.setColumns(readJsonList(def.getColumnDefsJson()));
        dto.setDefaultBox(readJsonMap(def.getBoxJson()));
        return dto;
    }

    private Map<String, Object> resolveSampleData(String packCode) {
        return PreviewSampleRegistry.sampleForPack(packCode);
    }

    private static String resolvePipeline(String packCode) {
        if (ReportPackConstants.CHARGE_RECEIPT.equals(packCode) || ReportPackConstants.IV_INFUSION_LABEL.equals(packCode)
                || ReportPackConstants.BEDSIDE_SPECIMEN_BARCODE.equals(packCode) || ReportPackConstants.PATIENT_WRISTBAND.equals(packCode)
                || ReportPackConstants.DEPOSIT_RECEIPT.equals(packCode) || ReportPackConstants.OUTPATIENT_REFUND_RECEIPT.equals(packCode)
                || ReportPackConstants.REGISTRATION_RECEIPT.equals(packCode)) {
            return "roll";
        }
        return "page";
    }

    private Map<String, Object> readJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json.trim(), new TypeReference<>() {});
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<Map<String, Object>> readJsonList(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json.trim(), new TypeReference<>() {});
        } catch (Exception ignored) {
            return null;
        }
    }
}
