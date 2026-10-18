/**
 * 门诊中药处方打印饮片重量口径。
 *
 * 产品/红旗默认 false：tcmHerbRows 用发放总量（slip.quantity）。
 * 国基 override 为 true：用每付重量（slip.doseValue）。
 *
 * 须以 `#/utils/print/outpatient-tcm-rx-print.config` 导入。
 */
export const TCM_RX_PRINT_HERB_WEIGHT_PER_DOSE = false;
