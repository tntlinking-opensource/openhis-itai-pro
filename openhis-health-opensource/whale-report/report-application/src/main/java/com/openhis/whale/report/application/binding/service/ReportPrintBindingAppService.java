package com.openhis.whale.report.application.binding.service;
import com.openhis.whale.report.application.template.service.ReportTemplateListService;

import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.report.application.binding.response.PrintBindingConfigDto;
import com.openhis.whale.report.application.binding.response.PrintBindingItemDto;
import com.openhis.whale.report.application.binding.response.PrintBindingTemplateOptionDto;
import com.openhis.whale.report.application.template.response.ReportTemplateListItemDto;
import com.openhis.whale.report.application.binding.response.ResolvePrintBindingResultDto;
import com.openhis.whale.report.application.template.request.ListTemplatesRequest;
import com.openhis.whale.report.application.binding.request.ResolvePrintBindingRequest;
import com.openhis.whale.report.application.binding.request.SavePrintBindingConfigRequest;
import com.openhis.whale.report.domain.binding.PrintBindingCatalog;
import com.openhis.whale.report.domain.binding.PrintBindingDef;
import com.openhis.whale.report.domain.entity.PrintBinding;
import com.openhis.whale.report.domain.service.PrintBindingManager;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 院点打印模板绑定 → {@code POST /api/app/reportPrintBinding/*}
 */
@Service
@Validated
public class ReportPrintBindingAppService {

    private final PrintBindingManager bindingManager;
    private final ReportTemplateListService templateListService;
    private final TenantContext tenantContext;

    public ReportPrintBindingAppService(
            PrintBindingManager bindingManager,
            ReportTemplateListService templateListService,
            TenantContext tenantContext) {
        this.bindingManager = bindingManager;
        this.templateListService = templateListService;
        this.tenantContext = tenantContext;
    }

    /**
     * 院点打印绑定配置（含默认模板与候选列表） → {@code POST /api/app/reportPrintBinding/getConfig}
     *
     * @return 全部 binding 项及当前生效模板
     */
    public PrintBindingConfigDto getConfig() {
        Long tenantId = tenantContext.getCurrentTenantId();
        List<ReportTemplateListItemDto> templates = templateListService.list(new ListTemplatesRequest());
        Map<String, ReportTemplateListItemDto> templateIndex = templates.stream()
                .filter(t -> StringUtils.hasText(t.getCode()))
                .collect(Collectors.toMap(t -> t.getCode().trim(), t -> t, (a, b) -> a, LinkedHashMap::new));

        Set<String> overrideCodes = bindingManager.listTenantOverrides(tenantId).stream()
                .map(PrintBinding::getBindingCode)
                .collect(Collectors.toCollection(HashSet::new));

        PrintBindingConfigDto dto = new PrintBindingConfigDto();
        List<PrintBindingItemDto> items = new ArrayList<>();
        for (PrintBindingDef def : PrintBindingCatalog.listAll()) {
            PrintBindingItemDto item = new PrintBindingItemDto();
            item.setBindingCode(def.bindingCode());
            item.setBindingName(def.name());
            item.setBizModule(def.bizModule());
            item.setScene(def.scene());
            item.setDefaultTemplateCode(def.defaultTemplateCode());
            String effective = bindingManager.resolveTemplateCode(def.bindingCode(), tenantId);
            item.setTemplateCode(effective);
            item.setTenantOverride(overrideCodes.contains(def.bindingCode()));
            item.setCandidates(buildCandidates(def, templateIndex, templates));
            items.add(item);
        }
        dto.setItems(items);
        return dto;
    }

    /**
     * 保存院点 binding 覆盖 → {@code POST /api/app/reportPrintBinding/saveConfig}
     *
     * @param request bindingCode → templateCode 映射
     * @return 保存后的完整配置
     */
    public PrintBindingConfigDto saveConfig(@Valid SavePrintBindingConfigRequest request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        bindingManager.saveBindings(tenantId, request.getBindings());
        return getConfig();
    }

    /**
     * 业务侧解析 binding 生效模板 → {@code POST /api/app/reportPrintBinding/resolve}
     *
     * @param request binding 编码
     * @return 默认与院点覆盖后的 templateCode
     */
    public ResolvePrintBindingResultDto resolve(@Valid ResolvePrintBindingRequest request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        String bindingCode = request.getBindingCode().trim();
        PrintBindingDef def = PrintBindingCatalog.require(bindingCode);
        ResolvePrintBindingResultDto dto = new ResolvePrintBindingResultDto();
        dto.setBindingCode(bindingCode);
        dto.setDefaultTemplateCode(def.defaultTemplateCode());
        dto.setTemplateCode(bindingManager.resolveTemplateCode(bindingCode, tenantId));
        boolean override = bindingManager.listTenantOverrides(tenantId).stream()
                .anyMatch(row -> bindingCode.equals(row.getBindingCode()));
        dto.setTenantOverride(override);
        return dto;
    }

    private List<PrintBindingTemplateOptionDto> buildCandidates(
            PrintBindingDef def,
            Map<String, ReportTemplateListItemDto> templateIndex,
            List<ReportTemplateListItemDto> allTemplates) {
        List<PrintBindingTemplateOptionDto> candidates = new ArrayList<>();
        Set<String> added = new HashSet<>();

        for (String code : def.suggestedTemplateCodes()) {
            addCandidate(candidates, added, templateIndex.get(code));
        }
        addCandidate(candidates, added, templateIndex.get(def.defaultTemplateCode()));

        String effective = bindingManager.resolveTemplateCode(def.bindingCode(), tenantContext.getCurrentTenantId());
        addCandidate(candidates, added, templateIndex.get(effective));

        for (ReportTemplateListItemDto template : allTemplates) {
            if (!"published".equalsIgnoreCase(template.getStatus())) {
                continue;
            }
            addCandidate(candidates, added, template);
        }
        return candidates;
    }

    private static void addCandidate(
            List<PrintBindingTemplateOptionDto> candidates,
            Set<String> added,
            ReportTemplateListItemDto template) {
        if (template == null || !StringUtils.hasText(template.getCode())) {
            return;
        }
        String code = template.getCode().trim();
        if (!added.add(code)) {
            return;
        }
        PrintBindingTemplateOptionDto option = new PrintBindingTemplateOptionDto();
        option.setCode(code);
        option.setName(template.getName());
        option.setPipeline(template.getPipeline());
        option.setLayoutMode(template.getLayoutMode());
        option.setStatus(template.getStatus());
        option.setSource(template.getSource());
        candidates.add(option);
    }
}
