/**
 * 住院医生站是否允许多人页签同时打开。
 *
 * 产品/红旗默认 false：同时只看一名患者，换人走未保存确认。
 * 国基 override 为 true：顶栏多开，各患者保留自己的病历和功能页。
 *
 * 须以 `#/views/inpatient/doctor-station/inpatient-doctor-multi-session.config` 导入，相对路径不走院点 override。
 */
export const INPATIENT_DOCTOR_MULTI_SESSION_ENABLED = false;
