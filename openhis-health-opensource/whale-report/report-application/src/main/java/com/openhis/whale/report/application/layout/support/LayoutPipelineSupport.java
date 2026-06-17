package com.openhis.whale.report.application.layout.support;

import com.openhis.whale.report.application.layout.model.LayoutDocument;
import com.openhis.whale.report.application.layout.model.LayoutPage;
import com.openhis.whale.report.domain.constants.ReportPipelineConstants;
import com.openhis.whale.report.domain.entity.ReportTemplate;
import com.openhis.whale.report.domain.constants.ReportPackConstants;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 将模板表 pipeline、layoutJson、模板编码与 pack 对齐，避免 roll 小票按 A4 渲染。
 */
@Component
public class LayoutPipelineSupport {

    private static final Set<String> ROLL_TEMPLATE_CODES = Set.of(
            ReportPackConstants.CHARGE_RECEIPT,
            ReportPackConstants.REGISTRATION_RECEIPT,
            ReportPackConstants.OUTPATIENT_REFUND_RECEIPT,
            ReportPackConstants.DEPOSIT_RECEIPT,
            ReportPackConstants.IV_INFUSION_LABEL,
            ReportPackConstants.PATIENT_WRISTBAND,
            ReportPackConstants.BEDSIDE_SPECIMEN_BARCODE,
            "appointment_queue_ticket");

    private static final Set<String> ROLL_PACK_CODES = ReportPackConstants.ROLL_PIPELINE_PACKS;

    /**
     * 渲染前统一解析 pipeline：roll 小票优先于错误的 template.pipeline=page。
     */
    public void normalizeForRender(
            LayoutDocument document,
            ReportTemplate template,
            String templateCode,
            String packCode) {
        if (document == null) {
            return;
        }
        if (shouldRenderAsRoll(document, template, templateCode, packCode)) {
            document.setPipeline(ReportPipelineConstants.PIPE_ROLL);
            applyRollPageDefaults(document.getPage());
            return;
        }
        if (template != null && StringUtils.hasText(template.getPipeline())) {
            applyTemplatePipeline(document, template);
        }
    }

    public void applyTemplatePipeline(LayoutDocument document, ReportTemplate template) {
        if (document == null || template == null || !StringUtils.hasText(template.getPipeline())) {
            return;
        }
        String templatePipeline = template.getPipeline().trim();
        document.setPipeline(templatePipeline);
        if (ReportPipelineConstants.PIPE_ROLL.equalsIgnoreCase(templatePipeline)) {
            applyRollPageDefaults(document.getPage());
        } else if (ReportPipelineConstants.PIPE_PAGE.equalsIgnoreCase(templatePipeline)) {
            applyPageDefaults(document.getPage());
        }
    }

    public void syncTemplatePipelineFromLayout(ReportTemplate template, LayoutDocument document) {
        if (template == null || document == null || !StringUtils.hasText(document.getPipeline())) {
            return;
        }
        template.setPipeline(document.getPipeline().trim());
    }

    public static boolean shouldRenderAsRoll(
            LayoutDocument document,
            ReportTemplate template,
            String templateCode,
            String packCode) {
        if (document == null) {
            return false;
        }
        if (document.getPipeline() != null && ReportPipelineConstants.PIPE_ROLL.equalsIgnoreCase(document.getPipeline().trim())) {
            return true;
        }
        LayoutPage page = document.getPage();
        if (page != null && page.getWidthMm() != null && page.getWidthMm() > 0) {
            return !ReportPipelineConstants.LAYOUT_MODE_OVERLAY.equalsIgnoreCase(page.getLayoutMode());
        }
        if (template != null && StringUtils.hasText(template.getPipeline())
                && ReportPipelineConstants.PIPE_ROLL.equalsIgnoreCase(template.getPipeline().trim())) {
            return true;
        }
        if (StringUtils.hasText(templateCode) && ROLL_TEMPLATE_CODES.contains(templateCode.trim())) {
            return true;
        }
        return StringUtils.hasText(packCode) && ROLL_PACK_CODES.contains(packCode.trim());
    }

    private static void applyRollPageDefaults(LayoutPage page) {
        if (page == null) {
            return;
        }
        if (page.getWidthMm() == null || page.getWidthMm() <= 0) {
            page.setWidthMm(ReportPipelineConstants.DEFAULT_ROLL_WIDTH_MM);
        }
        page.setSize(null);
        page.setOrientation(null);
        if (page.getMarginMm() == null || page.getMarginMm().isEmpty()) {
            page.setMarginMm(List.of(2f, 2f, 2f, 2f));
        }
        if (!StringUtils.hasText(page.getLayoutMode())) {
            page.setLayoutMode(ReportPipelineConstants.LAYOUT_MODE_FLOW);
        }
    }

    private static void applyPageDefaults(LayoutPage page) {
        if (page == null) {
            return;
        }
        page.setWidthMm(null);
        if (!StringUtils.hasText(page.getSize())) {
            page.setSize("A4");
        }
        if (!StringUtils.hasText(page.getOrientation())) {
            page.setOrientation("portrait");
        }
    }
}
