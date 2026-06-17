package com.openhis.whale.report.domain.service;

import com.openhis.whale.report.domain.constants.ReportPackConstants;
import com.openhis.whale.report.domain.constants.ReportPipelineConstants;
import com.openhis.whale.report.domain.constants.ReportTenantScope;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import com.openhis.whale.report.domain.repository.PrintElementDefRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 打印元素定义 pack：平台 seed + 院点扩展，按 layoutMode 过滤。
 */
@Service
public class PrintElementDefManager {

    private final PrintElementDefRepository repository;

    public PrintElementDefManager(PrintElementDefRepository repository) {
        this.repository = repository;
    }

    /** 加载 pack 元素索引（elementCode → def），院点未命中回退平台。 */
    public Map<String, PrintElementDef> loadPackIndex(String packCode, Long tenantId) {
        long effectiveTenant = ReportTenantScope.effectiveTenant(tenantId);
        List<PrintElementDef> defs = repository.findByTenantIdAndPackCodeAndIsDeletedFalseOrderBySortOrderAsc(
                effectiveTenant, packCode);
        if (defs.isEmpty() && effectiveTenant != ReportTenantScope.PLATFORM_TENANT_ID) {
            defs = repository.findByTenantIdAndPackCodeAndIsDeletedFalseOrderBySortOrderAsc(
                    ReportTenantScope.PLATFORM_TENANT_ID, packCode);
        }
        Map<String, PrintElementDef> index = new LinkedHashMap<>();
        for (PrintElementDef def : defs) {
            if (Boolean.FALSE.equals(def.getEnabled())) {
                continue;
            }
            index.put(def.getElementCode(), def);
        }
        return index;
    }

    public String resolvePackCode(String datasetCode, String layoutJson) {
        if (StringUtils.hasText(datasetCode)) {
            return datasetCode.trim();
        }
        return ReportPackConstants.DEFAULT_PACK;
    }

    public List<PrintElementDef> listPackElements(String packCode, Long tenantId, String layoutMode) {
        long effectiveTenant = ReportTenantScope.effectiveTenant(tenantId);
        if (StringUtils.hasText(layoutMode)
                && ReportPipelineConstants.LAYOUT_MODE_OVERLAY.equalsIgnoreCase(layoutMode.trim())) {
            return filterOverlayPackElements(loadAllPackElements(effectiveTenant, packCode));
        }
        if (StringUtils.hasText(layoutMode)) {
            List<PrintElementDef> defs = repository.findByTenantIdAndPackCodeAndLayoutModeAndIsDeletedFalseOrderBySortOrderAsc(
                    effectiveTenant, packCode, layoutMode.trim());
            if (defs.isEmpty() && effectiveTenant != ReportTenantScope.PLATFORM_TENANT_ID) {
                defs = repository.findByTenantIdAndPackCodeAndLayoutModeAndIsDeletedFalseOrderBySortOrderAsc(
                        ReportTenantScope.PLATFORM_TENANT_ID, packCode, layoutMode.trim());
            }
            return defs.stream().filter(d -> !Boolean.FALSE.equals(d.getEnabled())).toList();
        }
        return loadAllPackElements(effectiveTenant, packCode);
    }

    private List<PrintElementDef> loadAllPackElements(long effectiveTenant, String packCode) {
        List<PrintElementDef> defs = repository.findByTenantIdAndPackCodeAndIsDeletedFalseOrderBySortOrderAsc(
                effectiveTenant, packCode);
        if (defs.isEmpty() && effectiveTenant != ReportTenantScope.PLATFORM_TENANT_ID) {
            defs = repository.findByTenantIdAndPackCodeAndIsDeletedFalseOrderBySortOrderAsc(
                    ReportTenantScope.PLATFORM_TENANT_ID, packCode);
        }
        return defs.stream().filter(d -> !Boolean.FALSE.equals(d.getEnabled())).toList();
    }

    /**
     * overlay 设计器：layout_mode=overlay 的元素 + 带 box_json 的 flow 共用元素（如处方诊断/明细表）。
     */
    private static List<PrintElementDef> filterOverlayPackElements(List<PrintElementDef> allDefs) {
        List<PrintElementDef> out = new ArrayList<>();
        for (PrintElementDef def : allDefs) {
            if (ReportPipelineConstants.LAYOUT_MODE_OVERLAY.equalsIgnoreCase(def.getLayoutMode())) {
                out.add(def);
                continue;
            }
            if (StringUtils.hasText(def.getBoxJson())) {
                out.add(def);
            }
        }
        return out;
    }
}
