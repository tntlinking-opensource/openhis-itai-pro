package com.openhis.whale.report.application.pdf;

public final class ReportPdfUnitUtils {

    private static final float MM_TO_PT = 72f / 25.4f;

    private ReportPdfUnitUtils() {
    }

    public static float mmToPt(float mm) {
        return mm * MM_TO_PT;
    }

    public static float ptToMm(float pt) {
        return pt / MM_TO_PT;
    }
}
