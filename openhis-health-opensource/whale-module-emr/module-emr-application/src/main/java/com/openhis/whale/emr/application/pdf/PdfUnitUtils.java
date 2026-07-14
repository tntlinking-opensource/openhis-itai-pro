/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.pdf;

/**
 * 毫米与 PDF 点（pt）换算。
 */
public final class PdfUnitUtils {

    private static final float MM_TO_PT = 72f / 25.4f;

    private PdfUnitUtils() {
    }

    public static float mmToPt(float mm) {
        return mm * MM_TO_PT;
    }
}
