package com.openhis.whale.report.application.design.service;

import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.report.application.design.request.GetElementPackRequest;
import com.openhis.whale.report.application.design.request.PreviewRequest;
import com.openhis.whale.report.application.design.request.UploadGuideImageRequest;
import com.openhis.whale.report.application.design.request.ValidateLayoutRequest;
import com.openhis.whale.report.application.design.response.ElementPackDto;
import com.openhis.whale.report.application.design.response.GuideImageUploadDto;
import com.openhis.whale.report.application.design.response.PreviewResultDto;
import com.openhis.whale.report.application.design.response.ValidateLayoutResultDto;
import com.openhis.whale.report.application.layout.model.LayoutDocument;
import com.openhis.whale.report.application.layout.model.LayoutJsonParser;
import com.openhis.whale.report.application.layout.support.LayoutValidator;
import com.openhis.whale.report.application.pdf.PrintLayoutRenderService;
import com.openhis.whale.report.application.preview.PreviewDatasetResolver;
import com.openhis.whale.report.application.support.PrintGuideImageStore;
import com.openhis.whale.report.application.template.request.ListTemplatesRequest;
import com.openhis.whale.report.application.template.response.ReportTemplateListItemDto;
import com.openhis.whale.report.application.template.service.ReportTemplateListService;
import com.openhis.whale.report.domain.constants.ReportPackConstants;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import com.openhis.whale.report.domain.entity.ReportTemplate;
import com.openhis.whale.report.domain.service.ReportTemplateManager;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 排版设计器 AppService（对应设计器 UI 页面）→ {@code /api/app/reportDesign/*}
 */
@Service
@Validated
public class ReportDesignAppService {

    private final PrintLayoutRenderService printLayoutRenderService;
    private final ReportTemplateListService templateListService;
    private final ReportTemplateManager templateManager;
    private final TenantContext tenantContext;
    private final LayoutJsonParser layoutJsonParser;
    private final PrintElementPackMapper packMapper;
    private final LayoutValidator layoutValidator;
    private final PreviewDatasetResolver previewDatasetResolver;
    private final PrintGuideImageStore printGuideImageStore;

    public ReportDesignAppService(
            PrintLayoutRenderService printLayoutRenderService,
            ReportTemplateListService templateListService,
            ReportTemplateManager templateManager,
            TenantContext tenantContext,
            LayoutJsonParser layoutJsonParser,
            PrintElementPackMapper packMapper,
            LayoutValidator layoutValidator,
            PreviewDatasetResolver previewDatasetResolver,
            PrintGuideImageStore printGuideImageStore) {
        this.printLayoutRenderService = printLayoutRenderService;
        this.templateListService = templateListService;
        this.templateManager = templateManager;
        this.tenantContext = tenantContext;
        this.layoutJsonParser = layoutJsonParser;
        this.packMapper = packMapper;
        this.layoutValidator = layoutValidator;
        this.previewDatasetResolver = previewDatasetResolver;
        this.printGuideImageStore = printGuideImageStore;
    }

    /**
     * 元素包定义（设计器左侧元素树） → {@code POST /api/app/reportDesign/getElementPack}
     */
    public ElementPackDto getElementPack(GetElementPackRequest request) {
        GetElementPackRequest req = request;
        if (req == null) {
            req = new GetElementPackRequest();
        }
        Long tenantId = tenantContext.getCurrentTenantId();
        return packMapper.toPackDto(req.getPackCode(), tenantId, req.getLayoutMode());
    }

    /**
     * 校验 layoutJson 与元素包一致性 → {@code POST /api/app/reportDesign/validateLayout}
     */
    public ValidateLayoutResultDto validateLayout(ValidateLayoutRequest request) {
        if (request == null || !StringUtils.hasText(request.getLayoutJson())) {
            throw new ValidationException("layoutJson 不能为空");
        }
        Long tenantId = tenantContext.getCurrentTenantId();
        LayoutDocument doc = layoutJsonParser.parse(request.getLayoutJson());
        String packCode = ReportPackConstants.DEFAULT_PACK;
        if (StringUtils.hasText(request.getPackCode())) {
            packCode = request.getPackCode().trim();
        }
        Map<String, PrintElementDef> packIndex = packMapper.loadPackIndex(packCode, tenantId);
        boolean forPublish = Boolean.TRUE.equals(request.getForPublish());
        List<String> errors = layoutValidator.validate(doc, packIndex, forPublish);
        return new ValidateLayoutResultDto(errors.isEmpty(), errors);
    }

    /**
     * 打印模板列表（兼容） → {@code POST /api/app/reportDesign/listTemplates}
     */
    public List<ReportTemplateListItemDto> listTemplates(ListTemplatesRequest request) {
        return templateListService.list(request);
    }

    /**
     * 设计器/客户端 PDF 预览 → {@code POST /api/app/reportDesign/preview}
     */
    public PreviewResultDto preview(@Valid PreviewRequest request) {
        if (!StringUtils.hasText(request.getTemplateCode()) && !StringUtils.hasText(request.getLayoutJson())) {
            throw new ValidationException("templateCode 与 layoutJson 至少填一项");
        }
        if (request.getUseSampleData() == null && StringUtils.hasText(request.getLayoutJson())) {
            request.setUseSampleData(true);
        }

        Long tenantId = tenantContext.getCurrentTenantId();
        String packCode = resolvePackCode(request, tenantId);
        Map<String, Object> dataset = previewDatasetResolver.resolve(request, packCode);

        PrintLayoutRenderService.RenderResult rendered = printLayoutRenderService.render(
                request.getTemplateCode(),
                request.getLayoutJson(),
                packCode,
                tenantId,
                dataset);

        byte[] pdf = rendered.pdfBytes();
        String base64 = Base64.getEncoder().encodeToString(pdf);
        String hash = sha256Hex(pdf);
        String fileNamePrefix = ReportPackConstants.PREVIEW_FILE_PREFIX;
        if (StringUtils.hasText(request.getTemplateCode())) {
            fileNamePrefix = request.getTemplateCode().trim();
        }
        String fileName = fileNamePrefix + ".pdf";

        return new PreviewResultDto(
                "application/pdf", fileName, base64, hash, rendered.previewMeta());
    }

    /**
     * 套打定位纸上传 → {@code POST /api/app/reportDesign/uploadGuideImage}
     */
    public GuideImageUploadDto uploadGuideImage(@Valid UploadGuideImageRequest request) throws java.io.IOException {
        String url = printGuideImageStore.saveImage(
                request.getContentBase64(),
                request.getFileName(),
                request.getMimeType());
        GuideImageUploadDto dto = new GuideImageUploadDto();
        dto.setUrl(url);
        return dto;
    }

    private String resolvePackCode(PreviewRequest request, Long tenantId) {
        if (request == null) {
            return ReportPackConstants.DEFAULT_PACK;
        }
        if (StringUtils.hasText(request.getPackCode())) {
            return request.getPackCode().trim();
        }
        if (StringUtils.hasText(request.getTemplateCode())) {
            try {
                ReportTemplate t = templateManager.findPublished(request.getTemplateCode().trim(), tenantId, null);
                if (StringUtils.hasText(t.getDatasetCode())) {
                    return t.getDatasetCode().trim();
                }
            } catch (Exception ignored) {
                // inline layoutJson path
            }
        }
        return ReportPackConstants.DEFAULT_PACK;
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(java.util.Arrays.hashCode(data));
        }
    }
}
