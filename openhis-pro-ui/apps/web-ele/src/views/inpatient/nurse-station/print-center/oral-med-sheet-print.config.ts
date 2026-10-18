/**
 * 患者用药单出纸方式。
 * 产品默认 false：多名患者合成一次打印（国基模板改写后读 patientBlocks）。
 * 红旗 override 为 true：西药按患者一张；每张中药方单独一张。
 * PrintData 同时给 patientHeadText / drugRows，对齐库模板「每人一张」。
 * 须以 `#/views/inpatient/nurse-station/print-center/oral-med-sheet-print.config` 导入。
 */
export const ORAL_MED_SHEET_SPLIT_TCM_SHEETS = false;
