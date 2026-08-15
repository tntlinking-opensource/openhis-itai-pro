import type { SaveHomepageParams } from '#/api/mrms/types';

/**
 * 病案首页编辑表单模型。
 * P0 正式列字段与 SaveHomepageParams 对齐；其余国标扩展仍进 extension。
 */
export interface HomepageFormModel extends SaveHomepageParams {
  /** 病案号（展示/回填，主档冗余） */
  medcasno?: string;
  /** 患者主键（诊断目录弹窗等） */
  patientId?: string;
  /** 医疗付费方式 */
  payWayCode?: string;
  /** 健康卡号 */
  healthCardNo?: string;
  /** 住院次数 */
  visitTimes?: number;
  /** 年龄（岁） */
  ageYear?: number;
  /** 不足1周岁年龄（月） */
  ageMonth?: number;
  /** 出生地省市区 code */
  birplcCodes?: string[];
  /** 籍贯省市 code */
  naplCodes?: string[];
  /** 现住址省市区 */
  currAddrCodes?: string[];
  /** 现住址详细地址 */
  currAddrDetail?: string;
  /** 现住址邮编 */
  currAddrPoscode?: string;
  /** 户口地址省市区 */
  resdAddrCodes?: string[];
  /** 户口详细地址 */
  resdAddrDetail?: string;
  /** 户口邮编 */
  resdAddrPoscode?: string;
  /** 工作单位及地址 */
  emprAddr?: string;
  /** 单位电话 */
  emprTel?: string;
  /** 单位邮编 */
  emprPoscode?: string;
  /** 实施临床路径 1是 2否 */
  clinicalPathCode?: string;
  /** 使用医疗机构中药制剂 */
  tcmPrepCode?: string;
  /** 使用中医诊疗设备 */
  tcmDeviceCode?: string;
  /** 使用中医诊疗技术 */
  tcmTechCode?: string;
  /** 辨证施护 */
  syndromeNurseCode?: string;
  /** 入院病区 */
  admWard?: string;
  /** 入院床位 */
  admBed?: string;
  /** 出院病区 */
  dscgWard?: string;
  /** 出院床位 */
  dscgBed?: string;
  /** 药物过敏 1无 2有 */
  drugAllergyCode?: string;
  /** 过敏药物 */
  allergyDrugName?: string;
  /** 病理号 */
  pathologyNo?: string;
  /** 死亡患者尸检 1是 2否 */
  autopsyCode?: string;
  /** 血型 ABO */
  aboCode?: string;
  /** Rh */
  rhCode?: string;
  /** 进修医师 */
  trainDrName?: string;
  /** 实习医师 */
  intnDrName?: string;
  /** 编码员 */
  codrName?: string;
  /** 病案质量 */
  medcasQltCode?: string;
  /** 质控日期 */
  qltctrlDate?: string;
  /** 质控医师 */
  qltctrlDrName?: string;
  /** 质控护士 */
  qltctrlNursName?: string;
  /** 拟接收医院 */
  acpMedinsName?: string;
  /** 拟接收社区/卫生院 */
  acpCommunityName?: string;
  /** 31天再住院计划 1无 2有 */
  rinp31Flag?: string;
  /** 再住院目的 */
  rinp31Purpose?: string;
  /** 医疗费总额（extension / 4401） */
  medfee_sumamt?: number;
  /** 自付金额 */
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
 * 仍写入 extension 的键（表无正式列的国标扩展）。
 * P0 正式列不要放这里。
 */
export const HOMEPAGE_EXTENSION_KEYS: (keyof HomepageFormModel)[] = [
  'medcasno',
  'payWayCode',
  'healthCardNo',
  'visitTimes',
  'ageYear',
  'ageMonth',
  'birplcCodes',
  'naplCodes',
  'currAddrCodes',
  'currAddrDetail',
  'currAddrPoscode',
  'resdAddrCodes',
  'resdAddrDetail',
  'resdAddrPoscode',
  'emprAddr',
  'emprTel',
  'emprPoscode',
  'clinicalPathCode',
  'tcmPrepCode',
  'tcmDeviceCode',
  'tcmTechCode',
  'syndromeNurseCode',
  'admWard',
  'admBed',
  'dscgWard',
  'dscgBed',
  'drugAllergyCode',
  'allergyDrugName',
  'pathologyNo',
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
  'medfee_sumamt',
  'selfpay_amt',
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
];

/** 4401 费用分项键（与术语 mr_settlement_type_code 1～28 对齐） */
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

/** 费用金额类 extension 键（回填时转 number） */
export const HOMEPAGE_FEE_AMOUNT_KEYS: (keyof HomepageFormModel)[] = [
  'medfee_sumamt',
  'selfpay_amt',
  ...HOMEPAGE_FEE_ITEM_KEYS,
];
