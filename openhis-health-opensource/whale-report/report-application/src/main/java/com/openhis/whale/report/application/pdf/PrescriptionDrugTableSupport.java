package com.openhis.whale.report.application.pdf;

import com.openhis.whale.report.application.layout.model.LayoutBox;
import com.openhis.whale.report.application.layout.model.LayoutItem;

/**
 * 门诊处方药品明细表：固定行槽 + 空行斜线（方案 B，最多 5 种药品）。
 */
public final class PrescriptionDrugTableSupport {

    public static final String ELEMENT_CODE = "prescriptionDrugTable";
    public static final int DEFAULT_MAX_DRUG_ROWS = 5;

    private PrescriptionDrugTableSupport() {
    }

    public static boolean isPrescriptionDrugTable(String elementCode) {
        return ELEMENT_CODE.equals(elementCode);
    }

    /**
     * 是否启用固定行槽 + 空位斜线模式。
     */
    public static boolean useFixedSlotsWithSlash(String elementCode, LayoutItem item, LayoutBox effectiveBox) {
        if (!isPrescriptionDrugTable(elementCode)) {
            return false;
        }
        return resolveMaxRows(item, effectiveBox) > 0;
    }

    public static int resolveMaxRows(LayoutItem item, LayoutBox effectiveBox) {
        if (item != null && item.getBox() != null && item.getBox().getMaxRows() != null
                && item.getBox().getMaxRows() > 0) {
            return item.getBox().getMaxRows();
        }
        if (effectiveBox != null && effectiveBox.getMaxRows() != null && effectiveBox.getMaxRows() > 0) {
            return effectiveBox.getMaxRows();
        }
        return DEFAULT_MAX_DRUG_ROWS;
    }

    /**
     * 固定行槽模式下行高（mm）= 落格高度 / maxRows。
     */
    public static float resolveFixedRowHeightMm(LayoutBox box, int maxRows) {
        float heightMm = box != null && box.getHeightMm() != null ? box.getHeightMm() : 0f;
        if (heightMm > 0f && maxRows > 0) {
            return heightMm / maxRows;
        }
        if (box != null && box.getRowHeightMm() != null && box.getRowHeightMm() > 0f) {
            return box.getRowHeightMm();
        }
        return 4f;
    }
}
