package com.openhis.whale.report.application.template.service;

import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.report.application.template.response.PreviewLayoutMetaDto;
import com.openhis.whale.report.application.template.response.ReportTemplateListItemDto;
import com.openhis.whale.report.application.pdf.LayoutPreviewMetaExtractor;
import com.openhis.whale.report.application.layout.model.LayoutDocument;
import com.openhis.whale.report.application.layout.model.LayoutJsonParser;
import com.openhis.whale.report.application.template.request.ListTemplatesRequest;
import com.openhis.whale.report.domain.constants.ReportPackConstants;
import com.openhis.whale.report.domain.constants.ReportTenantScope;
import com.openhis.whale.report.domain.entity.ReportTemplate;
import com.openhis.whale.report.domain.service.ReportTemplateManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReportTemplateListService {

    private static final long PLATFORM_TENANT_ID = ReportTenantScope.PLATFORM_TENANT_ID;

    private static final Pattern CATALOG_NAME_PATTERN = Pattern.compile("^【(P\\d)(?:·([A-Z]+-\\d+))?】");

    private final ReportTemplateManager templateManager;
    private final TenantContext tenantContext;
    private final LayoutJsonParser layoutJsonParser;
    private final LayoutPreviewMetaExtractor previewMetaExtractor;

    public ReportTemplateListService(
            ReportTemplateManager templateManager,
            TenantContext tenantContext,
            LayoutJsonParser layoutJsonParser,
            LayoutPreviewMetaExtractor previewMetaExtractor) {
        this.templateManager = templateManager;
        this.tenantContext = tenantContext;
        this.layoutJsonParser = layoutJsonParser;
        this.previewMetaExtractor = previewMetaExtractor;
    }

    /**
     * 合并平台与院点模板列表（含 catalog 优先级排序与过滤）。
     *
     * @param request 可选过滤：priority、status、pipeline、keyword
     * @return 列表项 DTO
     */
    public List<ReportTemplateListItemDto> list(ListTemplatesRequest request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        ListTemplatesRequest filters = request;
        if (filters == null) {
            filters = new ListTemplatesRequest();
        }
        List<ReportTemplateListItemDto> items = new ArrayList<>();
        for (ReportTemplate template : templateManager.listMergedForTenant(tenantId)) {
            if (!matchesListFilters(template, filters)) {
                continue;
            }
            items.add(toListItem(template));
        }
        items.sort(Comparator
                .comparing(ReportTemplateListItemDto::getName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(ReportTemplateListItemDto::getCode, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    private ReportTemplateListItemDto toListItem(ReportTemplate template) {
        ReportTemplateListItemDto dto = new ReportTemplateListItemDto();
        dto.setId(template.getId());
        dto.setCode(template.getCode());
        dto.setName(template.getName());
        dto.setPipeline(template.getPipeline());
        dto.setDatasetCode(template.getDatasetCode());
        dto.setStatus(template.getStatus());
        dto.setVersion(template.getVersion());
        dto.setTenantId(template.getTenantId());
        dto.setUpdatedAt(template.getUpdatedAt());
        long tplTenant = PLATFORM_TENANT_ID;
        if (template.getTenantId() != null) {
            tplTenant = template.getTenantId();
        }
        if (tplTenant == PLATFORM_TENANT_ID) {
            dto.setSource(ReportPackConstants.TEMPLATE_SOURCE_PLATFORM);
        } else {
            dto.setSource(ReportPackConstants.TEMPLATE_SOURCE_TENANT);
        }
        applyCatalogMeta(dto, template.getName());
        if (StringUtils.hasText(template.getLayoutJson())) {
            try {
                LayoutDocument doc = layoutJsonParser.parse(template.getLayoutJson());
                PreviewLayoutMetaDto meta = previewMetaExtractor.extract(doc);
                dto.setLayoutMode(meta.getLayoutMode());
                dto.setPageSize(meta.getPageSize());
                dto.setOrientation(meta.getOrientation());
            } catch (Exception ignored) {
                dto.setLayoutMode("flow");
            }
        } else {
            dto.setLayoutMode("flow");
        }
        return dto;
    }

    private static void applyCatalogMeta(ReportTemplateListItemDto dto, String name) {
        if (!StringUtils.hasText(name)) {
            return;
        }
        Matcher matcher = CATALOG_NAME_PATTERN.matcher(name.trim());
        if (matcher.find()) {
            dto.setPriority(matcher.group(1));
            if (matcher.group(2) != null) {
                dto.setBizSeq(matcher.group(2));
            }
        }
    }

    private static boolean matchesListFilters(ReportTemplate template, ListTemplatesRequest request) {
        if (StringUtils.hasText(request.getPriority())) {
            ReportTemplateListItemDto probe = new ReportTemplateListItemDto();
            applyCatalogMeta(probe, template.getName());
            String want = request.getPriority().trim();
            if (probe.getPriority() == null || !want.equalsIgnoreCase(probe.getPriority())) {
                return false;
            }
        }
        if (StringUtils.hasText(request.getStatus())) {
            String want = request.getStatus().trim().toLowerCase(Locale.ROOT);
            String actual = "";
            if (template.getStatus() != null) {
                actual = template.getStatus().trim().toLowerCase(Locale.ROOT);
            }
            if (!want.equals(actual)) {
                return false;
            }
        }
        if (StringUtils.hasText(request.getPipeline())) {
            String want = request.getPipeline().trim().toLowerCase(Locale.ROOT);
            String actual = "";
            if (template.getPipeline() != null) {
                actual = template.getPipeline().trim().toLowerCase(Locale.ROOT);
            }
            if (!want.equals(actual)) {
                return false;
            }
        }
        if (StringUtils.hasText(request.getKeyword())) {
            String kw = request.getKeyword().trim().toLowerCase(Locale.ROOT);
            String code = "";
            if (template.getCode() != null) {
                code = template.getCode().toLowerCase(Locale.ROOT);
            }
            String name = "";
            if (template.getName() != null) {
                name = template.getName().toLowerCase(Locale.ROOT);
            }
            if (!code.contains(kw) && !name.contains(kw)) {
                return false;
            }
        }
        return true;
    }
}
