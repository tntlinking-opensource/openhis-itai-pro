/**
 * 临时医嘱单是否把已执行记录的执行时间写入 PrintData.executeAtText。
 * 产品默认关：其它院点模板未绑该列，保持空串。
 * 国基 override 为 true，配合该院点库模板「执行时间」列。
 * 须以 `#/views/inpatient/nurse-station/print-center/temp-order-print.config` 导入。
 */
export const TEMP_ORDER_PRINT_FILL_EXECUTE_TIME = false;
