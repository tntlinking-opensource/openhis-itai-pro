package com.openhis.whale.report.domain.binding;

import com.openhis.whale.core.application.exception.BusinessException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务打印绑定点目录（随版本扩展，院点覆盖存 rpt_print_binding）。
 */
public final class PrintBindingCatalog {

    public static final String OUTPATIENT_CHARGE_PRINT = "outpatient_charge_print";
    public static final String OUTPATIENT_PRESCRIPTION_PRINT = "outpatient_prescription_print";
    public static final String OUTPATIENT_CHARGE_LIST_PRINT = "outpatient_charge_list_print";
    public static final String INPATIENT_DAILY_CHARGE_PRINT = "inpatient_daily_charge_print";

    private static final Map<String, PrintBindingDef> INDEX = buildIndex();

    private PrintBindingCatalog() {
    }

    public static List<PrintBindingDef> listAll() {
        return new ArrayList<>(INDEX.values());
    }

    public static PrintBindingDef require(String bindingCode) {
        String lookupKey = "";
        if (bindingCode != null) {
            lookupKey = bindingCode.trim();
        }
        PrintBindingDef def = INDEX.get(lookupKey);
        if (def == null) {
            throw new BusinessException("RPT_BINDING_UNKNOWN", "未知打印绑定点: " + bindingCode);
        }
        return def;
    }

    public static boolean exists(String bindingCode) {
        return bindingCode != null && INDEX.containsKey(bindingCode.trim());
    }

    private static Map<String, PrintBindingDef> buildIndex() {
        Map<String, PrintBindingDef> map = new LinkedHashMap<>();
        map.put(OUTPATIENT_CHARGE_PRINT, new PrintBindingDef(
                OUTPATIENT_CHARGE_PRINT,
                "门诊收费结算打印",
                "门诊收费",
                "收费成功后凭条/票据联打印",
                "charge_receipt",
                List.of("charge_receipt", "outpatient_invoice_overlay")));
        map.put(OUTPATIENT_PRESCRIPTION_PRINT, new PrintBindingDef(
                OUTPATIENT_PRESCRIPTION_PRINT,
                "门诊处方打印",
                "门诊发药",
                "发药处方单 / 套打 / 发药单",
                "outpatient_prescription_flow",
                List.of(
                        "outpatient_prescription_flow",
                        "outpatient_prescription_overlay",
                        "outpatient_prescription_tcm_flow",
                        "outpatient_prescription_tcm_overlay",
                        "outpatient_prescription_narcotic_flow",
                        "outpatient_prescription_narcotic_overlay",
                        "dispense_slip")));
        map.put(OUTPATIENT_CHARGE_LIST_PRINT, new PrintBindingDef(
                OUTPATIENT_CHARGE_LIST_PRINT,
                "门诊费用清单",
                "门诊收费",
                "费用清单打印",
                "outpatient_charge_list",
                List.of("outpatient_charge_list")));
        map.put(INPATIENT_DAILY_CHARGE_PRINT, new PrintBindingDef(
                INPATIENT_DAILY_CHARGE_PRINT,
                "住院日费用清单",
                "住院护士站",
                "日费用清单打印",
                "inpatient_daily_charge_list",
                List.of("inpatient_daily_charge_list")));
        return Map.copyOf(map);
    }
}
