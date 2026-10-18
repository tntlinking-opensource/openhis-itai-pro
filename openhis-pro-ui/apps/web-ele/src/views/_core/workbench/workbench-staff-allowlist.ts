/**
 * 非平台管理员工作台快捷入口白名单（zxplat_menus.code / 路由 name）。
 * 仅这些菜单可出现在工作台；展示时再与个人菜单权限求交，并按侧栏父级分组。
 */
export const WORKBENCH_STAFF_ALLOWLIST = new Set<string>([
  // 无子集工作站：有权限即直接展示
  'OutpatientDoctorStation',
  'InpatientDoctorStation',
  'OutpatientNurseStation',
  'InpatientNurseStation',
  'MedicalTechWorkstation',

  // 收费处（门诊）
  'ChargeOpRegister',
  'ChargeOpCharge',
  'ChargeOpRefund',
  'ChargeOpDayClose',

  // 发药 / 药房业务（门诊）
  'DispenseOp',
  'MedicineReturnOp',
  'OutpatientPrescriptionView',
  'OpPharmacyDailySettlement',

  // 药库单据
  'WhPurchaseOrder',
  'WhPurchaseInbound',
  'WhStockOut',

  // 药房单据
  'PhRequisition',
  'PhStockIn',
  'PhStocktakeOrder',

  // 科室单据
  'DeptPurchaseRequest',
  'DeptRequisition',
  'DeptStockIn',
  'DeptStocktakeOrder',
  'DeptGainLoss',

  // 报表 / 明细
  'OpFeeDetail',
  'InvStockList',
  'DispenseRecordOp',
  'OutpatientRegistrationDetail',

  // 基础数据
  'Medicine',
  'Diagnosistreatment',
  'Device',
  'Supplier',
]);

/** 无侧栏父级时的兜底分组（如门诊医生站等顶级菜单） */
export const WORKBENCH_STAFF_ROOT_GROUP_ID = 'workstation';
export const WORKBENCH_STAFF_ROOT_GROUP_TITLE = '工作站';
