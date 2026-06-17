package com.openhis.whale.report.application.template.service;
import com.openhis.whale.report.application.design.service.PrintElementPackMapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.report.application.template.response.ReportTemplateListItemDto;
import com.openhis.whale.report.application.template.response.TemplateDesignDto;
import com.openhis.whale.report.application.template.request.ListTemplatesRequest;
import com.openhis.whale.report.application.layout.support.LayoutCopyTransformer;
import com.openhis.whale.report.application.layout.support.LayoutValidator;
import com.openhis.whale.report.application.layout.model.LayoutDocument;
import com.openhis.whale.report.application.layout.model.LayoutJsonParser;
import com.openhis.whale.report.application.template.request.CopyTemplateAsNewRequest;
import com.openhis.whale.report.application.template.request.ForkTemplateDraftRequest;
import com.openhis.whale.report.application.template.request.GetTemplateDesignRequest;
import com.openhis.whale.report.application.template.request.PublishTemplateRequest;
import com.openhis.whale.report.application.template.request.SaveTemplateDraftRequest;
import com.openhis.whale.report.domain.constants.ReportPackConstants;
import com.openhis.whale.report.domain.constants.ReportPackLayoutSupport;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import com.openhis.whale.report.domain.entity.ReportTemplate;
import com.openhis.whale.report.domain.service.PrintElementDefManager;
import com.openhis.whale.report.domain.service.ReportTemplateManager;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

/**
 * 打印模板管理 → {@code POST /api/app/reportTemplate/*}
 */
@Service
@Validated
public class ReportTemplateAppService {

    private final ReportTemplateManager templateManager;
    private final ReportTemplateListService templateListService;
    private final TenantContext tenantContext;
    private final LayoutJsonParser layoutJsonParser;
    private final LayoutCopyTransformer layoutCopyTransformer;
    private final LayoutValidator layoutValidator;
    private final PrintElementPackMapper packMapper;
    private final PrintElementDefManager elementDefManager;
    private final ObjectMapper objectMapper;

    public ReportTemplateAppService(
            ReportTemplateManager templateManager,
            ReportTemplateListService templateListService,
            TenantContext tenantContext,
            LayoutJsonParser layoutJsonParser,
            LayoutCopyTransformer layoutCopyTransformer,
            LayoutValidator layoutValidator,
            PrintElementPackMapper packMapper,
            PrintElementDefManager elementDefManager,
            ObjectMapper objectMapper) {
        this.templateManager = templateManager;
        this.templateListService = templateListService;
        this.tenantContext = tenantContext;
        this.layoutJsonParser = layoutJsonParser;
        this.layoutCopyTransformer = layoutCopyTransformer;
        this.layoutValidator = layoutValidator;
        this.packMapper = packMapper;
        this.elementDefManager = elementDefManager;
        this.objectMapper = objectMapper;
    }

    /**
     * 打印模板列表 → {@code POST /api/app/reportTemplate/getList}
     *
     * @param request 过滤条件，可为 null
     * @return 模板列表项
     */
    public List<ReportTemplateListItemDto> getList(ListTemplatesRequest request) {
        return templateListService.list(request);
    }

    /**
     * 加载设计器编辑态模板 → {@code POST /api/app/reportTemplate/getForDesign}
     *
     * @param request 模板编码
     * @return 含 layoutJson 的设计 DTO
     */
    public TemplateDesignDto getForDesign(@Valid GetTemplateDesignRequest request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        ReportTemplate template = templateManager.resolveForDesign(request.getTemplateCode().trim(), tenantId);
        return toDesignDto(template, tenantId);
    }

    /**
     * 从已发布版本 fork 草稿 → {@code POST /api/app/reportTemplate/forkDraft}
     *
     * @param request 源模板编码
     * @return 新草稿设计 DTO
     */
    public TemplateDesignDto forkDraft(@Valid ForkTemplateDraftRequest request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        ReportTemplate draft = templateManager.forkDraftFromPublished(request.getTemplateCode().trim(), tenantId);
        return toDesignDto(draft, tenantId);
    }

    /**
     * 复制为新模板并切换排版模式 → {@code POST /api/app/reportTemplate/copyAsNew}
     *
     * @param request 源模板、新编码与目标 layoutMode
     * @return 新建模板设计 DTO
     */
    public TemplateDesignDto copyAsNew(@Valid CopyTemplateAsNewRequest request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        String sourceCode = request.getSourceTemplateCode().trim();
        ReportTemplate source = templateManager.resolveForDesign(sourceCode, tenantId);
        LayoutDocument sourceDoc = layoutJsonParser.parse(source.getLayoutJson());
        String targetLayoutMode = request.getLayoutMode().trim();
        String packCode = ReportPackLayoutSupport.resolvePackForLayoutMode(
                resolvePackCode(source.getDatasetCode()),
                targetLayoutMode);
        List<PrintElementDef> targetPack = elementDefManager.listPackElements(packCode, tenantId, targetLayoutMode);
        if (targetPack.isEmpty()) {
            throw new ValidationException("目标排版模式下无可用元素包: " + packCode);
        }
        LayoutDocument transformed = layoutCopyTransformer.transform(
                sourceDoc,
                targetLayoutMode,
                request.getPipeline(),
                targetPack);
        String layoutJson = writeLayoutJson(transformed);
        validateLayoutJson(layoutJson, packCode, tenantId, false);
        ReportTemplate created = templateManager.copyAsNewTemplate(
                sourceCode,
                request.getNewTemplateCode().trim(),
                request.getNewTemplateName(),
                layoutJson,
                transformed.getPipeline(),
                packCode,
                tenantId);
        return toDesignDto(created, tenantId);
    }

    /**
     * 保存设计器草稿 layoutJson → {@code POST /api/app/reportTemplate/saveDraft}
     *
     * @param request 模板 id 与 layoutJson
     * @return 保存后的设计 DTO
     */
    public TemplateDesignDto saveDraft(@Valid SaveTemplateDraftRequest request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        ReportTemplate template = templateManager.requireEditableForDesign(request.getTemplateId(), tenantId);
        // Gate：已发布态 fork 出的草稿保存时仍走发布级校验
        boolean forPublish = ReportTemplateManager.STATUS_PUBLISHED.equals(template.getStatus());
        LayoutDocument doc = layoutJsonParser.parse(request.getLayoutJson());
        validateLayoutJson(request.getLayoutJson(), template.getDatasetCode(), tenantId, forPublish);
        ReportTemplate saved = templateManager.saveDraftLayout(
                request.getTemplateId(),
                tenantId,
                request.getLayoutJson(),
                doc.getPipeline());
        return toDesignDto(saved, tenantId);
    }

    /**
     * 发布草稿模板 → {@code POST /api/app/reportTemplate/publish}
     *
     * @param request 待发布模板 id
     * @return 发布后设计 DTO
     */
    public TemplateDesignDto publish(@Valid PublishTemplateRequest request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        ReportTemplate draft = templateManager.requireDraftForEdit(request.getTemplateId(), tenantId);
        validateLayoutJson(draft.getLayoutJson(), draft.getDatasetCode(), tenantId, true);
        ReportTemplate published = templateManager.publishDraft(request.getTemplateId(), tenantId);
        return toDesignDto(published, tenantId);
    }

    private void validateLayoutJson(String layoutJson, String datasetCode, Long tenantId, boolean forPublish) {
        LayoutDocument doc = layoutJsonParser.parse(layoutJson);
        String packCode = resolvePackCode(datasetCode);
        Map<String, PrintElementDef> packIndex = packMapper.loadPackIndex(packCode, tenantId);
        List<String> errors = layoutValidator.validate(doc, packIndex, forPublish);
        if (!errors.isEmpty()) {
            throw new ValidationException(String.join("；", errors));
        }
    }

    private TemplateDesignDto toDesignDto(ReportTemplate template, Long tenantId) {
        TemplateDesignDto dto = new TemplateDesignDto();
        dto.setId(template.getId());
        dto.setCode(template.getCode());
        dto.setName(template.getName());
        dto.setPipeline(template.getPipeline());
        dto.setDatasetCode(template.getDatasetCode());
        dto.setStatus(template.getStatus());
        dto.setVersion(template.getVersion());
        dto.setTenantId(template.getTenantId());
        dto.setEditable(templateManager.isEditableDraft(template, tenantId));
        dto.setSource(resolveTemplateSource(template.getTenantId()));
        dto.setLayoutJson(readLayoutAsMap(template.getLayoutJson()));
        return dto;
    }

    private static String resolvePackCode(String datasetCode) {
        if (StringUtils.hasText(datasetCode)) {
            return datasetCode.trim();
        }
        return ReportPackConstants.DEFAULT_PACK;
    }

    private static String resolveTemplateSource(Long tenantId) {
        long tplTenant = 0L;
        if (tenantId != null) {
            tplTenant = tenantId;
        }
        if (tplTenant == 0L) {
            return ReportPackConstants.TEMPLATE_SOURCE_PLATFORM;
        }
        return ReportPackConstants.TEMPLATE_SOURCE_TENANT;
    }

    private String writeLayoutJson(LayoutDocument document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (Exception e) {
            throw new ValidationException("layoutJson 序列化失败");
        }
    }

    private Map<String, Object> readLayoutAsMap(String layoutJson) {
        if (!StringUtils.hasText(layoutJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(layoutJson.trim(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new ValidationException("layoutJson 解析失败");
        }
    }
}
