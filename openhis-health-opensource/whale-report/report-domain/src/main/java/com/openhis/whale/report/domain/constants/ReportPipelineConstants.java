package com.openhis.whale.report.domain.constants;

/**
 * 报表排版 pipeline / layoutMode 常量（report 域内收口，避免 application 散落 magic string）。
 */
public final class ReportPipelineConstants {

    /** 分页 A4/A5 等固定纸型管线 */
    public static final String PIPE_PAGE = "page";

    /** 热敏 roll 流式 + 绝对层叠加管线 */
    public static final String PIPE_ROLL = "roll";

    /** 流式排版（roll 小票、A4 flow 单证） */
    public static final String LAYOUT_MODE_FLOW = "flow";

    /** 套打 overlay（预印纸绝对坐标） */
    public static final String LAYOUT_MODE_OVERLAY = "overlay";

    /** 热敏纸默认宽度 mm */
    public static final float DEFAULT_ROLL_WIDTH_MM = 80f;

    /** roll 设计/渲染默认页高 mm（OpenPDF 长纸，Stamper 裁切） */
    public static final float DEFAULT_ROLL_HEIGHT_MM = 400f;

    private ReportPipelineConstants() {
    }
}
