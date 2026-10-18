/**
 * 体温单底部补充行与录入项（院点可经 `#/` override 本文件）。
 *
 * 产品/红旗：大便、小便、入量、其他、尿量、体重、身高。
 * 国基纸质单另有引流量、过敏药物、备注（无「其他」）。
 *
 * 须以 `#/views/inpatient/nurse-station/temperature-sheet/temperature-sheet-footer.config` 导入。
 */

export type TemperatureSheetFooterSupplementField =
  | 'allergyDrug'
  | 'drainage'
  | 'height'
  | 'intake'
  | 'other'
  | 'remark'
  | 'stoolCount'
  | 'urine'
  | 'urineCount'
  | 'weight';

export interface TemperatureSheetFooterSupplementRow {
  field: TemperatureSheetFooterSupplementField;
  label: string;
}

/** 底部补充指标行（除呼吸、血压外） */
export const TEMPERATURE_SHEET_FOOTER_SUPPLEMENT_ROWS: readonly TemperatureSheetFooterSupplementRow[] =
  [
    { field: 'stoolCount', label: '大便次数' },
    { field: 'urineCount', label: '小便次数' },
    { field: 'intake', label: '总入量(ml)' },
    { field: 'other', label: '其他(ml)' },
    { field: 'urine', label: '尿量(ml)' },
    { field: 'weight', label: '体重(kg)' },
    { field: 'height', label: '身高(cm)' },
  ];

/** 前一日统计归属的补充项：录入日 D 的 22:00 槽保存，底部展示在 D-1 列 */
export const TEMPERATURE_SHEET_PREVIOUS_DAY_SUPPLEMENT_FIELDS: readonly TemperatureSheetFooterSupplementField[] =
  ['stoolCount', 'urineCount', 'intake', 'other', 'urine'];

/** 录入区是否展示引流量 */
export const TEMPERATURE_SHEET_SHOW_DRAINAGE_INPUT = false;

/** 录入区是否展示过敏药物 */
export const TEMPERATURE_SHEET_SHOW_ALLERGY_DRUG_INPUT = false;

/** 录入区是否展示备注（国基；主线用「其他」） */
export const TEMPERATURE_SHEET_SHOW_REMARK_INPUT = false;

/** 录入区是否展示其他(ml) */
export const TEMPERATURE_SHEET_SHOW_OTHER_INPUT = true;
