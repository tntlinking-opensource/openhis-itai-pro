package com.openhis.whale.report.application.pdf;

import com.openhis.whale.report.application.template.response.PreviewLayoutMetaDto;
import com.openhis.whale.report.application.layout.support.LayoutPipelineSupport;
import com.openhis.whale.report.application.layout.model.LayoutDocument;
import com.openhis.whale.report.application.layout.model.LayoutJsonParser;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import com.openhis.whale.report.domain.entity.ReportTemplate;
import com.openhis.whale.report.domain.service.PrintElementDefManager;
import com.openhis.whale.report.domain.service.ReportTemplateManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 解析 layoutJson、加载元素包并委托 {@link LayoutJsonPageRenderer} 输出 PDF。
 */
@Service
public class PrintLayoutRenderService {

    private final LayoutJsonParser layoutJsonParser;
    private final LayoutJsonPageRenderer pageRenderer;
    private final ReportTemplateManager templateManager;
    private final PrintElementDefManager elementDefManager;
    private final LayoutPreviewMetaExtractor previewMetaExtractor;
    private final LayoutPipelineSupport layoutPipelineSupport;

    public PrintLayoutRenderService(
            LayoutJsonParser layoutJsonParser,
            LayoutJsonPageRenderer pageRenderer,
            ReportTemplateManager templateManager,
            PrintElementDefManager elementDefManager,
            LayoutPreviewMetaExtractor previewMetaExtractor,
            LayoutPipelineSupport layoutPipelineSupport) {
        this.layoutJsonParser = layoutJsonParser;
        this.pageRenderer = pageRenderer;
        this.templateManager = templateManager;
        this.elementDefManager = elementDefManager;
        this.previewMetaExtractor = previewMetaExtractor;
        this.layoutPipelineSupport = layoutPipelineSupport;
    }

    /**
     * 渲染预览或打印 PDF。
     *
     * @param templateCode     已发布模板编码；与 inlineLayoutJson 二选一
     * @param inlineLayoutJson 设计器 inline layout；优先于 templateCode 取 layout
     * @param packCodeHint     元素包编码提示；为空时从模板 datasetCode 推断
     * @param tenantId         租户 id
     * @param dataset          打印数据集 root
     * @return PDF 字节、实际 packCode 与预览元数据
     */
    public RenderResult render(
            String templateCode,
            String inlineLayoutJson,
            String packCodeHint,
            Long tenantId,
            Map<String, Object> dataset) {
        String layoutJson = templateManager.resolveLayoutJson(templateCode, inlineLayoutJson, tenantId);
        LayoutDocument document = layoutJsonParser.parse(layoutJson);
        ReportTemplate template = null;
        if (!StringUtils.hasText(inlineLayoutJson) && StringUtils.hasText(templateCode)) {
            try {
                template = templateManager.findPublished(templateCode.trim(), tenantId, null);
            } catch (Exception ignored) {
                // inline-only or missing published template
            }
        }
        String packCode = packCodeHint;
        if (!StringUtils.hasText(packCode) && template != null && StringUtils.hasText(template.getDatasetCode())) {
            packCode = template.getDatasetCode();
        }
        if (!StringUtils.hasText(packCode) && StringUtils.hasText(templateCode)) {
            try {
                if (template == null) {
                    template = templateManager.findPublished(templateCode.trim(), tenantId, null);
                }
                if (template != null && StringUtils.hasText(template.getDatasetCode())) {
                    packCode = template.getDatasetCode();
                }
            } catch (Exception ignored) {
                // inline layout without published template
            }
        }
        packCode = elementDefManager.resolvePackCode(packCode, layoutJson);
        layoutPipelineSupport.normalizeForRender(document, template, templateCode, packCode);
        Map<String, PrintElementDef> elementIndex = elementDefManager.loadPackIndex(packCode, tenantId);
        byte[] pdf = pageRenderer.render(document, dataset, elementIndex);
        PreviewLayoutMetaDto previewMeta = previewMetaExtractor.extract(document);
        return new RenderResult(pdf, packCode, previewMeta);
    }

    public record RenderResult(byte[] pdfBytes, String packCode, PreviewLayoutMetaDto previewMeta) {
    }
}
