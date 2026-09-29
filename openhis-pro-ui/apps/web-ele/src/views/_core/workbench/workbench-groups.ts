import type { WorkbenchGroupConfig } from './workbench-types';

/**
 * 平台管理员工作台分组与菜单映射（非管理员走 workbench-staff-allowlist + 菜单树父级分组）。
 * menuCodes 须与 whale_menus.code / 路由 name 一致。
 */
export const WORKBENCH_GROUPS: WorkbenchGroupConfig[] = [
  {
    id: 'doctor',
    title: '医生',
    subtitle: '临床诊疗与病历',
    icon: 'lucide:stethoscope',
    accentColor: '#0066e6',
    accentGradient: 'linear-gradient(135deg, #3b82f6 0%, #0066e6 100%)',
    iconBgColors: ['#eff6ff', '#e8f0fe', '#dbeafe', '#d6e4ff'],
    iconColors: ['#0066e6', '#1d5bb8', '#1e4a9e', '#1e3a7a'],
    menuCodes: [
      'OutpatientDoctorStation',
      'InpatientDoctorStation',
      'order_template',
      'EmrTemplate',
      'OpFeeDetail',
    ],
  },
  {
    id: 'nurse',
    title: '护士',
    subtitle: '护理执行与病区管理',
    icon: 'lucide:heart-pulse',
    accentColor: '#0066e6',
    accentGradient: 'linear-gradient(135deg, #3b82f6 0%, #0066e6 100%)',
    iconBgColors: ['#f0f4ff', '#e8eeff', '#e0eaff', '#d8e6ff'],
    iconColors: ['#0066e6', '#1d5bb8', '#1e4a9e', '#1e3a7a'],
    menuCodes: ['OutpatientNurseStation', 'InpatientNurseStation'],
  },
  {
    id: 'medical-tech',
    title: '医技',
    subtitle: '医技执行与计费',
    icon: 'lucide:microscope',
    accentColor: '#0066e6',
    accentGradient: 'linear-gradient(135deg, #3b82f6 0%, #0066e6 100%)',
    iconBgColors: ['#f0f7ff', '#e8f1ff', '#e0ebff', '#d8e5ff'],
    iconColors: ['#0066e6', '#1d5bb8', '#1e4a9e', '#1e3a7a'],
    menuCodes: ['MedicalTechWorkstation'],
  },
  {
    id: 'cashier',
    title: '收费处',
    subtitle: '挂号收费与结算',
    icon: 'lucide:wallet',
    accentColor: '#0066e6',
    accentGradient: 'linear-gradient(135deg, #3b82f6 0%, #0066e6 100%)',
    iconBgColors: ['#f5f8ff', '#eef3ff', '#e8efff', '#e2ebff'],
    iconColors: ['#0066e6', '#1d5bb8', '#1e4a9e', '#1e3a7a'],
    menuCodes: [
      'ChargeOpRegister',
      'ChargeOpCharge',
      'ChargeOpRefund',
      'ChargeOpDayClose',
      'ChargeIpRegister',
      'ChargeIpSettle',
      'ChargeIpPrepay',
      'ChargeIpFee',
      'ChargeIpDayClose',
    ],
  },
  {
    id: 'pharmacy',
    title: '药房',
    subtitle: '发药退药与记录',
    icon: 'lucide:pill',
    accentColor: '#0066e6',
    accentGradient: 'linear-gradient(135deg, #3b82f6 0%, #0066e6 100%)',
    iconBgColors: ['#eff6ff', '#e5efff', '#dbeafe', '#d1e3ff'],
    iconColors: ['#0066e6', '#1d5bb8', '#1e4a9e', '#1e3a7a'],
    menuCodes: [
      'DispenseOp',
      'DispenseIp',
      'MedicineReturnOp',
      'MedicineReturnIp',
      'DispenseRecordOp',
      'DispenseRecordIp',
    ],
  },
  {
    id: 'admin',
    title: '管理员',
    subtitle: '系统配置与权限',
    icon: 'lucide:shield-check',
    accentColor: '#0066e6',
    accentGradient: 'linear-gradient(135deg, #3b82f6 0%, #0066e6 100%)',
    iconBgColors: ['#f8fafc', '#f1f5f9', '#e8edf3', '#e2e8f0'],
    iconColors: ['#0066e6', '#1d5bb8', '#1e4a9e', '#1e3a7a'],
    menuCodes: [
      'user_management',
      'role_management',
      'menu_management',
      'dept_management',
      'PrintTemplate',
      'setting_management',
    ],
  },
];

/** 概览页自身，不参与快捷入口展示 */
export const WORKBENCH_EXCLUDED_ROUTE_NAMES = new Set(['Analytics']);
