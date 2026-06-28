/**
 * 工作台菜单图标（参照设计稿 lucide 线型图标）。
 * key 为 whale_menus.code / 路由 name。
 */
export const WORKBENCH_MENU_ICONS: Record<string, string> = {
  ChargeIpDayClose: 'lucide:calendar-check',
  ChargeIpFee: 'lucide:receipt',
  ChargeIpPrepay: 'lucide:coins',
  ChargeIpRegister: 'lucide:bed',
  ChargeIpSettle: 'lucide:wallet',
  ChargeOpCharge: 'lucide:circle-dollar-sign',
  ChargeOpDayClose: 'lucide:calendar-check',
  ChargeOpRefund: 'lucide:undo-2',
  ChargeOpRegister: 'lucide:calendar-plus',
  dept_management: 'lucide:building-2',
  DispenseIp: 'lucide:pill',
  DispenseOp: 'lucide:pill',
  DispenseRecordIp: 'lucide:clipboard-list',
  DispenseRecordOp: 'lucide:clipboard-list',
  EmrTemplate: 'lucide:file-text',
  InpatientDoctorStation: 'lucide:building-2',
  InpatientNurseStation: 'lucide:bed',
  MedicineReturnIp: 'lucide:rotate-ccw',
  MedicineReturnOp: 'lucide:rotate-ccw',
  menu_management: 'lucide:menu',
  OpFeeDetail: 'lucide:badge-dollar-sign',
  order_template: 'lucide:layers',
  OutpatientDoctorStation: 'lucide:user-search',
  OutpatientNurseStation: 'lucide:clipboard-list',
  PrintTemplate: 'lucide:printer',
  role_management: 'lucide:shield',
  setting_management: 'lucide:settings',
  user_management: 'lucide:users',
};

/** 解析菜单图标：仅使用工作台映射表 */
export function resolveWorkbenchMenuIcon(menuCode: string) {
  return WORKBENCH_MENU_ICONS[menuCode] ?? 'lucide:layout-grid';
}
