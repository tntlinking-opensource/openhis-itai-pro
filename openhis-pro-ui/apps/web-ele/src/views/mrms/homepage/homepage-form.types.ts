import type { SaveHomepageParams } from '#/api/mrms/types';

/**
 * 病案首页编辑表单模型。
 * 持久化字段与 SaveHomepageParams / 正式列一对一；费用为首页快照回填，仅「重新统计」覆盖。
 */
export interface HomepageFormModel extends SaveHomepageParams {
  /** 病案号（展示/回填，主档冗余） */
  medcasno?: string;
  /** 患者主键（诊断目录弹窗等） */
  patientId?: string;
  /**
   * 入院科室主键（诊断医生下拉；列表行带入，不落首页表）。
   */
  organizationId?: string;
  /** 住院次数（主档 patnIptCnt 回显，不落首页列） */
  visitTimes?: number;
  /** 医疗费总额（费用快照，只读） */
  medfee_sumamt?: number;
  /** 自付金额（费用快照，只读） */
  selfpay_amt?: number;
  /** 一般医疗服务费 */
  ordn_med_servfee?: number;
  /** 一般治疗操作费 */
  ordn_trt_oprt_fee?: number;
  /** 护理费 */
  nurs_fee?: number;
  /** 综合医疗服务类其他费用 */
  com_med_serv_oth_fee?: number;
  /** 病理诊断费 */
  palg_diag_fee?: number;
  /** 实验室诊断费 */
  lab_diag_fee?: number;
  /** 影像学诊断费 */
  rdhy_diag_fee?: number;
  /** 临床诊断项目费 */
  clnc_dise_fee?: number;
  /** 非手术治疗项目费 */
  nsrgtrt_item_fee?: number;
  /** 临床物理治疗费 */
  clnc_phys_trt_fee?: number;
  /** 手术治疗费 */
  rgtrt_trt_fee?: number;
  /** 麻醉费 */
  anst_fee?: number;
  /** 手术费 */
  rgtrt_fee?: number;
  /** 康复费 */
  rhab_fee?: number;
  /** 中医治疗费 */
  tcm_trt_fee?: number;
  /** 西药费 */
  wm_fee?: number;
  /** 抗菌药物费用 */
  abtl_medn_fee?: number;
  /** 中成药费 */
  tcmpat_fee?: number;
  /** 中药饮片费 */
  tcmherb_fee?: number;
  /** 血费 */
  blo_fee?: number;
  /** 白蛋白类制品费 */
  albu_fee?: number;
  /** 球蛋白类制品费 */
  glon_fee?: number;
  /** 凝血因子类制品费 */
  clotfac_fee?: number;
  /** 细胞因子类制品费 */
  cyki_fee?: number;
  /** 检查用一次性医用材料费 */
  exam_dspo_matl_fee?: number;
  /** 治疗用一次性医用材料费 */
  trt_dspo_matl_fee?: number;
  /** 手术用一次性医用材料费 */
  oprn_dspo_matl_fee?: number;
  /** 其他费 */
  oth_fee?: number;
}

/**
 * 曾升正式列的键名清单（保存走标量）。
 * medcasno / visitTimes 仍以主档为准，不在此列。
 */
export const HOMEPAGE_PROMOTED_SCALAR_KEYS: (keyof HomepageFormModel)[] = [
  'payWayCode',
  'healthCardNo',
  'ageYear',
  'ageMonth',
  'birplcCodes',
  'birplcName',
  'naplCodes',
  'naplName',
  'currAddrCodes',
  'currAddrDetail',
  'currAddrPoscode',
  'resdAddrCodes',
  'resdAddrDetail',
  'resdAddrPoscode',
  'emprAddr',
  'emprTel',
  'emprPoscode',
  'admWard',
  'admBed',
  'dscgWard',
  'dscgBed',
  'drugAllergyCode',
  'allergyDrugName',
  'pathologyNo',
  'mainDiagCureCode',
  'rescueCount',
  'rescueSuccessCount',
  'clinicPathManageCode',
  'diagMatchOpDscg',
  'diagMatchAdmDscg',
  'diagMatchPrePost',
  'diagMatchClinicPath',
  'diagMatchRadioPath',
  'autopsyCode',
  'aboCode',
  'rhCode',
  'trainDrName',
  'intnDrName',
  'codrName',
  'medcasQltCode',
  'qltctrlDate',
  'qltctrlDrName',
  'qltctrlNursName',
  'acpMedinsName',
  'acpCommunityName',
  'rinp31Flag',
  'rinp31Purpose',
];

/** 4401 费用分项键（与术语 mr_settlement_type_code 1～28 对齐；快照回填） */
export const HOMEPAGE_FEE_ITEM_KEYS = [
  'ordn_med_servfee',
  'ordn_trt_oprt_fee',
  'nurs_fee',
  'com_med_serv_oth_fee',
  'palg_diag_fee',
  'lab_diag_fee',
  'rdhy_diag_fee',
  'clnc_dise_fee',
  'nsrgtrt_item_fee',
  'clnc_phys_trt_fee',
  'rgtrt_trt_fee',
  'anst_fee',
  'rgtrt_fee',
  'rhab_fee',
  'tcm_trt_fee',
  'wm_fee',
  'abtl_medn_fee',
  'tcmpat_fee',
  'tcmherb_fee',
  'blo_fee',
  'albu_fee',
  'glon_fee',
  'clotfac_fee',
  'cyki_fee',
  'exam_dspo_matl_fee',
  'trt_dspo_matl_fee',
  'oprn_dspo_matl_fee',
  'oth_fee',
] as const;

export type HomepageFeeItemKey = (typeof HOMEPAGE_FEE_ITEM_KEYS)[number];

/** 费用金额类键（快照回填时转 number；不经 saveHomepage） */
export const HOMEPAGE_FEE_AMOUNT_KEYS: (keyof HomepageFormModel)[] = [
  'medfee_sumamt',
  'selfpay_amt',
  ...HOMEPAGE_FEE_ITEM_KEYS,
];
