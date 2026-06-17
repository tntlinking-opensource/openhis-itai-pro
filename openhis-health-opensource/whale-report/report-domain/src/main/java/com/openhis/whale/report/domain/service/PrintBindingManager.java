package com.openhis.whale.report.domain.service;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.report.domain.binding.PrintBindingCatalog;
import com.openhis.whale.report.domain.binding.PrintBindingDef;
import com.openhis.whale.report.domain.constants.ReportTenantScope;
import com.openhis.whale.report.domain.entity.PrintBinding;
import com.openhis.whale.report.domain.repository.PrintBindingRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 院点打印绑定点覆盖：未配置时使用 {@link PrintBindingCatalog} 默认模板。
 */
@Service
public class PrintBindingManager {

    private final PrintBindingRepository repository;
    private final ReportTemplateManager templateManager;

    public PrintBindingManager(PrintBindingRepository repository, ReportTemplateManager templateManager) {
        this.repository = repository;
        this.templateManager = templateManager;
    }

    /** 全部绑定点在当前租户下的生效 templateCode。 */
    public Map<String, String> listEffectiveBindings(Long tenantId) {
        Map<String, String> effective = new HashMap<>();
        for (PrintBindingDef def : PrintBindingCatalog.listAll()) {
            effective.put(def.bindingCode(), resolveTemplateCode(def.bindingCode(), tenantId));
        }
        return effective;
    }

    /**
     * 解析绑定点生效模板：院点覆盖优先，否则 catalog 默认。
     */
    public String resolveTemplateCode(String bindingCode, Long tenantId) {
        PrintBindingDef def = PrintBindingCatalog.require(bindingCode);
        long effectiveTenant = ReportTenantScope.effectiveTenant(tenantId);
        if (effectiveTenant != ReportTenantScope.PLATFORM_TENANT_ID) {
            Optional<PrintBinding> override = repository.findByTenantIdAndBindingCodeAndIsDeletedFalse(
                    effectiveTenant, def.bindingCode());
            if (override.isPresent() && StringUtils.hasText(override.get().getTemplateCode())) {
                return override.get().getTemplateCode().trim();
            }
        }
        return def.defaultTemplateCode();
    }

    /** 批量保存院点 binding 覆盖，模板须已发布。 */
    public void saveBindings(Long tenantId, Map<String, String> bindings) {
        if (tenantId == null || tenantId == ReportTenantScope.PLATFORM_TENANT_ID) {
            throw new BusinessException("RPT_TENANT_REQUIRED", "院点租户才能保存打印绑定");
        }
        if (bindings == null || bindings.isEmpty()) {
            throw new BusinessException("RPT_BINDING_EMPTY", "绑定配置不能为空");
        }
        for (Map.Entry<String, String> entry : bindings.entrySet()) {
            String bindingCode = trimEntryKey(entry.getKey());
            String templateCode = trimEntryValue(entry.getValue());
            if (!StringUtils.hasText(bindingCode) || !StringUtils.hasText(templateCode)) {
                throw new BusinessException("RPT_BINDING_INVALID", "绑定点与模板编码均不能为空");
            }
            PrintBindingCatalog.require(bindingCode);
            assertTemplatePublished(templateCode, tenantId);
            upsertBinding(tenantId, bindingCode, templateCode);
        }
    }

    public List<PrintBinding> listTenantOverrides(Long tenantId) {
        if (tenantId == null || tenantId == ReportTenantScope.PLATFORM_TENANT_ID) {
            return List.of();
        }
        return repository.findByTenantIdAndIsDeletedFalseOrderByBindingCodeAsc(tenantId);
    }

    private void upsertBinding(Long tenantId, String bindingCode, String templateCode) {
        Optional<PrintBinding> existing = repository.findByTenantIdAndBindingCodeAndIsDeletedFalse(
                tenantId, bindingCode);
        PrintBinding row;
        if (existing.isPresent()) {
            row = existing.get();
            row.setTemplateCode(templateCode);
            row.setUpdatedAt(LocalDateTime.now());
        } else {
            row = new PrintBinding();
            row.setTenantId(tenantId);
            row.setBindingCode(bindingCode);
            row.setTemplateCode(templateCode);
            row.setCreatedAt(LocalDateTime.now());
            row.setUpdatedAt(LocalDateTime.now());
            row.setIsDeleted(false);
        }
        repository.save(row);
    }

    private void assertTemplatePublished(String templateCode, Long tenantId) {
        try {
            templateManager.findPublished(templateCode, tenantId, null);
        } catch (BusinessException ex) {
            throw new BusinessException(
                    "RPT_BINDING_TEMPLATE_NOT_FOUND",
                    "模板未发布或不存在: " + templateCode);
        }
    }

    private static String trimEntryKey(String key) {
        if (key != null) {
            return key.trim();
        }
        return "";
    }

    private static String trimEntryValue(String value) {
        if (value != null) {
            return value.trim();
        }
        return "";
    }
}
