/**
 * 住院结算页是否展示「医保结算单」打印。
 *
 * 产品/红旗默认 false。国基 override 为 true。
 * 须以 `#/views/inpatient/charge/settle/inpatient-settle-print.config` 导入。
 */
export const INPATIENT_SETTLE_INSURANCE_SHEET_PRINT_ENABLED = false;

/** 院点医院等级展示（票面；公共默认空，国基 override） */
export const INPATIENT_SETTLE_INSURANCE_SHEET_HOSPITAL_LEVEL = '';

/** 院点医疗机构编码（票面；公共默认空，国基 override） */
export const INPATIENT_SETTLE_INSURANCE_SHEET_HOSPITAL_CODE = '';
