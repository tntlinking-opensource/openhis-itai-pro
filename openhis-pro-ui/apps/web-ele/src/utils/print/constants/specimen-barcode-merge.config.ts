/**
 * 合管组内加急项（priorityEnum=EM）是否拆出单独打采血贴。
 *
 * 主线 false：合管仍一组一张；组内任一急则整贴印「急」。
 * 院点 true：加急项单独成贴（可单独点打）；其余合管项仍一组一张、不带急。
 *
 * 须以 `#/utils/print/constants/specimen-barcode-merge.config` 导入，相对路径不走院点 override。
 */
export const SPECIMEN_BARCODE_SPLIT_URGENT_FROM_MERGE = false;
