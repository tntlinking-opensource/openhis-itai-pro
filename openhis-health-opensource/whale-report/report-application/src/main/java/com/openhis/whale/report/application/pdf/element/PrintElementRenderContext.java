package com.openhis.whale.report.application.pdf.element;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfWriter;
import com.openhis.whale.report.application.pdf.ReportPdfFontFactory;
import com.openhis.whale.report.application.layout.model.LayoutItem;
import com.openhis.whale.report.application.layout.model.LayoutPage;
import com.openhis.whale.report.application.layout.model.LayoutStyle;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import lombok.Getter;

import java.util.Map;

/**
 * 单次 PDF 渲染会话上下文：页面尺寸、数据集、元素索引与 roll 绝对层测高。
 */
@Getter
public class PrintElementRenderContext {

    private final Document document;
    private final PdfWriter writer;
    private Rectangle pageSize;
    /** roll 渲染开始时页高（pt），绝对坐标换算基准 */
    private final float initialPageHeightPt;
    private final float initialPageWidthPt;
    private final float[] marginsPt;
    private final LayoutPage page;
    private final boolean rollMode;
    private final ReportPdfFontFactory fontFactory;
    private final Map<String, Object> dataset;
    private final Map<String, PrintElementDef> elementIndex;
    private float maxAbsoluteBottomMm;
    /** roll 裁切后的页高（pt），供预览元数据等使用 */
    private float rollPageHeightPt;

    public PrintElementRenderContext(
            Document document,
            PdfWriter writer,
            Rectangle pageSize,
            float[] marginsPt,
            LayoutPage page,
            ReportPdfFontFactory fontFactory,
            Map<String, Object> dataset,
            Map<String, PrintElementDef> elementIndex,
            boolean rollMode) {
        this.document = document;
        this.writer = writer;
        this.pageSize = pageSize;
        this.initialPageHeightPt = resolveHeight(pageSize);
        this.initialPageWidthPt = resolveWidth(pageSize);
        this.marginsPt = marginsPt;
        this.page = page;
        this.rollMode = rollMode;
        this.fontFactory = fontFactory;
        this.dataset = dataset;
        this.elementIndex = elementIndex;
    }

    public void noteAbsoluteBottomMm(float bottomMm) {
        if (bottomMm > maxAbsoluteBottomMm) {
            maxAbsoluteBottomMm = bottomMm;
        }
    }

    public void updatePageSize(Rectangle pageSize) {
        this.pageSize = pageSize;
    }

    public void setRollPageHeightPt(float rollPageHeightPt) {
        this.rollPageHeightPt = rollPageHeightPt;
    }

    public float rollPageWidthPt() {
        if (pageSize != null) {
            return pageSize.getWidth();
        }
        return initialPageWidthPt;
    }

    /** roll 绝对层坐标换算：始终用初始长纸页高，与 OpenPDF 当前页用户空间一致 */
    public float rollLayoutPageHeightPt() {
        if (initialPageHeightPt > 0f) {
            return initialPageHeightPt;
        }
        return rollPageHeightPt();
    }

    public float rollPageHeightPt() {
        if (rollPageHeightPt > 0f) {
            return rollPageHeightPt;
        }
        return resolveHeight(pageSize);
    }

    public PrintElementDef requireDef(LayoutItem item) {
        PrintElementDef def = elementIndex.get(item.getElementCode());
        if (def == null) {
            throw new IllegalArgumentException("未知元素: " + item.getElementCode());
        }
        return def;
    }

    public Font fontFor(LayoutItem item, PrintElementDef def, float defaultPt, int extraStyle) {
        LayoutStyle style = item.getStyle();
        float size = defaultPt;
        int styleFlags = extraStyle;
        if (style != null) {
            if (style.getFontSizePt() != null) {
                size = style.getFontSizePt();
            }
            if (Boolean.TRUE.equals(style.getBold())) {
                styleFlags |= Font.BOLD;
            }
        }
        return fontFactory.bodyFont(size, styleFlags);
    }

    private static float resolveHeight(Rectangle pageSize) {
        if (pageSize == null) {
            return 0f;
        }
        return pageSize.getHeight();
    }

    private static float resolveWidth(Rectangle pageSize) {
        if (pageSize == null) {
            return 0f;
        }
        return pageSize.getWidth();
    }
}
