package com.openhis.whale.report.domain.constants;

import java.util.Set;

/**
 * 打印元素 pack / 数据集编码常量（report 域内收口）。
 */
public final class ReportPackConstants {

    /** 默认元素 pack（门诊处方等未指定 pack 时的回退） */
    public static final String OUTPATIENT_PRESCRIPTION = "outpatient_prescription";
    public static final String DEFAULT_PACK = OUTPATIENT_PRESCRIPTION;

    /** 门诊收费小票 */
    public static final String CHARGE_RECEIPT = "charge_receipt";

    /** 挂号小票 */
    public static final String REGISTRATION_RECEIPT = "registration_receipt";

    /** 门诊退费小票 */
    public static final String OUTPATIENT_REFUND_RECEIPT = "outpatient_refund_receipt";

    /** 预交金收据 */
    public static final String DEPOSIT_RECEIPT = "deposit_receipt";

    /** 输液标签 */
    public static final String IV_INFUSION_LABEL = "iv_infusion_label";

    /** 床旁标本条码 */
    public static final String BEDSIDE_SPECIMEN_BARCODE = "bedside_specimen_barcode";

    /** 治疗执行单 */
    public static final String TREATMENT_EXECUTE_SHEET = "treatment_execute_sheet";

    /** 患者腕带 */
    public static final String PATIENT_WRISTBAND = "patient_wristband";

    /** 住院日费用清单 */
    public static final String INPATIENT_DAILY_CHARGE_LIST = "inpatient_daily_charge_list";

    /** 医嘱执行单 */
    public static final String ORDER_EXECUTE_SHEET = "order_execute_sheet";

    /** 住院费用汇总 */
    public static final String INPATIENT_CHARGE_SUMMARY = "inpatient_charge_summary";

    /** 住院发票套打 */
    public static final String INPATIENT_INVOICE_OVERLAY = "inpatient_invoice_overlay";

    /** 医保结算单 */
    public static final String INSURANCE_SETTLEMENT = "insurance_settlement";

    /** 门诊发票套打 */
    public static final String OUTPATIENT_INVOICE_OVERLAY = "outpatient_invoice_overlay";

    /** 门诊费用清单 */
    public static final String OUTPATIENT_CHARGE_LIST = "outpatient_charge_list";

    /** 病区发药汇总 */
    public static final String WARD_DISPENSE_SUMMARY = "ward_dispense_summary";

    /** 输液执行单 */
    public static final String IV_INFUSION_EXECUTE_SHEET = "iv_infusion_execute_sheet";

    /** 入院证 */
    public static final String ADMISSION_CERTIFICATE = "admission_certificate";

    /** 出院证 */
    public static final String DISCHARGE_CERTIFICATE = "discharge_certificate";

    /** 检验申请单 */
    public static final String LAB_APPLY_FORM = "lab_apply_form";

    /** 检查申请单 */
    public static final String EXAM_APPLY_FORM = "exam_apply_form";

    /** 处置申请单 */
    public static final String PROCEDURE_APPLY_FORM = "procedure_apply_form";

    /** 设计器 A4 占位 pack */
    public static final String PRINT_PAGE_STUB = "print_page_stub";

    /** 设计器热敏占位 pack */
    public static final String PRINT_ROLL_STUB = "print_roll_stub";

    /** 设计器套打占位 pack */
    public static final String PRINT_PAGE_OVERLAY_STUB = "print_page_overlay_stub";

    /** 设计器预览默认文件名前缀 */
    public static final String PREVIEW_FILE_PREFIX = "preview";

    /** 模板来源：出厂平台 */
    public static final String TEMPLATE_SOURCE_PLATFORM = "platform";

    /** 模板来源：院点租户 */
    public static final String TEMPLATE_SOURCE_TENANT = "tenant";

    /** 走 roll pipeline 的 pack 编码（与 {@link com.openhis.whale.report.application.layout.support.LayoutPipelineSupport} 对齐） */
    public static final Set<String> ROLL_PIPELINE_PACKS = Set.of(
            CHARGE_RECEIPT,
            REGISTRATION_RECEIPT,
            OUTPATIENT_REFUND_RECEIPT,
            DEPOSIT_RECEIPT,
            PRINT_ROLL_STUB);

    private ReportPackConstants() {
    }
}
