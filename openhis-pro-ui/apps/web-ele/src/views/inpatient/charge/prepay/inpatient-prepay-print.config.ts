/**
 * 预交金页是否额外打「住院预交金退补单」（三联单二等分，标记补/退）。
 *
 * 不替换原住院预交金收据。产品默认 false：只打收据，退费不打退补单。
 * 国基 override 为 true。
 * 须以 `#/views/inpatient/charge/prepay/inpatient-prepay-print.config` 导入。
 */
export const INPATIENT_PREPAY_CHANGE_SLIP_ENABLED = false;
