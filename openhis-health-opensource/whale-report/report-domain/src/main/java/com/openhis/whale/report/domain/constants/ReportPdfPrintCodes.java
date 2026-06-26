package com.openhis.whale.report.domain.constants;

/**
 * C 类报表绑定 Print 模块的 pdfPrintCode（与 Flyway 种子一致）。
 */
public final class ReportPdfPrintCodes {

    /** 门诊日结 PDF 模板。 */
    public static final String OUTPATIENT_DAILY_SETTLEMENT = "outpatient_daily_settlement_pdf";

    /** 住院日结 PDF 模板。 */
    public static final String INPATIENT_DAILY_SETTLEMENT = "inpatient_daily_settlement_pdf";

    private ReportPdfPrintCodes() {
    }
}
