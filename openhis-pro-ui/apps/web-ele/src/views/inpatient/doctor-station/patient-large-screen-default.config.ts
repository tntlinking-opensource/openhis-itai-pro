/**
 * 住院医生站：进入站点是否默认展开患者大屏。
 *
 * 产品/红旗默认 false：进站先进工作台，可点「患者一览」打开。
 * 国基 override 为 true：进站即展开大屏。
 *
 * 须以 `#/views/inpatient/doctor-station/patient-large-screen-default.config` 导入。
 */
export const DOCTOR_STATION_LARGE_SCREEN_DEFAULT_OPEN = false;
