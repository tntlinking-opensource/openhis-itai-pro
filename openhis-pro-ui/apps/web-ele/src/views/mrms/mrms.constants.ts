import type { FormItemOption } from '@vben-core/table-layout-ui';

import type { CaseRecordStatusCode } from '#/api/mrms/types';

/** 病案状态展示 */
export const CASE_RECORD_STATUS_OPTIONS: FormItemOption[] = [
  { label: '草稿', value: 'draft' },
  { label: '已提交', value: 'submitted' },
  { label: '已接收', value: 'received' },
  { label: '已退回', value: 'returned' },
  { label: '质控中', value: 'qcing' },
  { label: '已归档', value: 'archived' },
];

export const CASE_RECORD_STATUS_LABEL: Record<string, string> = {
  draft: '草稿',
  submitted: '已提交',
  received: '已接收',
  returned: '已退回',
  qcing: '质控中',
  archived: '已归档',
};

export const CASE_RECORD_STATUS_TAG: Record<string, 'danger' | 'info' | 'success' | 'warning'> = {
  draft: 'info',
  submitted: 'warning',
  received: 'success',
  returned: 'danger',
  qcing: 'warning',
  archived: 'success',
};

/** 首页诊断行类型 */
export const HOMEPAGE_DIAG_TYPES = [
  { code: 'discharge_main', label: '出院主诊断' },
  { code: 'discharge_other', label: '出院其他诊断' },
  { code: 'admission_main', label: '入院主诊断' },
  { code: 'admission_other', label: '入院其他诊断' },
  { code: 'pathology', label: '病理诊断' },
  { code: 'injury_poison', label: '损伤中毒外部原因' },
] as const;

export type HomepageDiagTypeCode = (typeof HOMEPAGE_DIAG_TYPES)[number]['code'];

/** 左侧锚点 / 主分块（与首页 Tab 一致） */
export const HOMEPAGE_ANCHOR_SECTIONS = [
  { id: 'sec-inpatient', label: '住院信息', section: 'basic' as const },
  { id: 'sec-diagnosis', label: '诊断记录', section: 'diagnosis' as const },
  { id: 'sec-fee', label: '费用结算', section: 'fee' as const },
  { id: 'sec-procedure', label: '手术/操作记录', section: 'procedure' as const },
  { id: 'sec-appendix', label: '病案附页', section: 'staff' as const },
];

/** 病案结算类型 → 费用分项展示（与 4401 / 术语 900010 对齐） */
export const HOMEPAGE_FEE_ITEM_OPTIONS: { key: string; label: string }[] = [
  { key: 'ordn_med_servfee', label: '一般医疗服务费' },
  { key: 'ordn_trt_oprt_fee', label: '一般治疗操作费' },
  { key: 'nurs_fee', label: '护理费' },
  { key: 'com_med_serv_oth_fee', label: '综合医疗服务类其他费用' },
  { key: 'palg_diag_fee', label: '病理诊断费' },
  { key: 'lab_diag_fee', label: '实验室诊断费' },
  { key: 'rdhy_diag_fee', label: '影像学诊断费' },
  { key: 'clnc_dise_fee', label: '临床诊断项目费' },
  { key: 'nsrgtrt_item_fee', label: '非手术治疗项目费' },
  { key: 'clnc_phys_trt_fee', label: '临床物理治疗费' },
  { key: 'rgtrt_trt_fee', label: '手术治疗费' },
  { key: 'anst_fee', label: '麻醉费' },
  { key: 'rgtrt_fee', label: '手术费' },
  { key: 'rhab_fee', label: '康复费' },
  { key: 'tcm_trt_fee', label: '中医治疗费' },
  { key: 'wm_fee', label: '西药费' },
  { key: 'abtl_medn_fee', label: '抗菌药物费用' },
  { key: 'tcmpat_fee', label: '中成药费' },
  { key: 'tcmherb_fee', label: '中药饮片费' },
  { key: 'blo_fee', label: '血费' },
  { key: 'albu_fee', label: '白蛋白类制品费' },
  { key: 'glon_fee', label: '球蛋白类制品费' },
  { key: 'clotfac_fee', label: '凝血因子类制品费' },
  { key: 'cyki_fee', label: '细胞因子类制品费' },
  { key: 'exam_dspo_matl_fee', label: '检查用一次性医用材料费' },
  { key: 'trt_dspo_matl_fee', label: '治疗用一次性医用材料费' },
  { key: 'oprn_dspo_matl_fee', label: '手术用一次性医用材料费' },
  { key: 'oth_fee', label: '其他费' },
];

/** 医疗付费方式 */
export const PAY_WAY_OPTIONS: FormItemOption[] = [
  { label: '城镇职工基本医疗保险', value: '1' },
  { label: '城镇居民基本医疗保险', value: '2' },
  { label: '新型农村合作医疗', value: '3' },
  { label: '贫困救助', value: '4' },
  { label: '商业医疗保险', value: '5' },
  { label: '全公费', value: '6' },
  { label: '全自费', value: '7' },
  { label: '其他社会保险', value: '8' },
  { label: '其他', value: '9' },
];

export const ADM_WAY_OPTIONS: FormItemOption[] = [
  { label: '急诊', value: '1' },
  { label: '门诊', value: '2' },
  { label: '其他医疗机构转入', value: '3' },
  { label: '其他', value: '9' },
];

export const DSCG_WAY_OPTIONS: FormItemOption[] = [
  { label: '医嘱离院', value: '1' },
  { label: '医嘱转院', value: '2' },
  { label: '医嘱转社区卫生服务机构/乡镇卫生院', value: '3' },
  { label: '非医嘱离院', value: '4' },
  { label: '死亡', value: '5' },
  { label: '其他', value: '9' },
];

export const GENDER_OPTIONS: FormItemOption[] = [
  { label: '男', value: '1' },
  { label: '女', value: '2' },
  { label: '未知', value: '0' },
  /** 兼容 HIS AdministrativeGender 存量回显 */
  { label: '男', value: 'MALE' },
  { label: '女', value: 'FEMALE' },
];

/** 入院病情（4401 adm_cond：1有 2临床未确定 3情况不明 4无） */
export const ADM_COND_OPTIONS: FormItemOption[] = [
  { label: '有', value: '1' },
  { label: '临床未确定', value: '2' },
  { label: '情况不明', value: '3' },
  { label: '无', value: '4' },
];

export const NTLY_OPTIONS: FormItemOption[] = [
  { label: '中国', value: '156' },
  { label: '其他', value: '999' },
];

export const NATY_OPTIONS: FormItemOption[] = [
  { label: '汉族', value: '01' },
  { label: '回族', value: '03' },
  { label: '满族', value: '11' },
  { label: '蒙古族', value: '02' },
  { label: '其他', value: '99' },
];

export const PRFS_OPTIONS: FormItemOption[] = [
  { label: '国家机关、党群组织、企业、事业单位负责人', value: '11' },
  { label: '专业技术人员', value: '13' },
  { label: '办事人员和有关人员', value: '17' },
  { label: '商业、服务业人员', value: '21' },
  { label: '农、林、牧、渔、水利业生产人员', value: '27' },
  { label: '生产、运输设备操作人员及有关人员', value: '31' },
  { label: '军人', value: '37' },
  { label: '不便分类的其他从业人员', value: '51' },
  { label: '个体经营者', value: '54' },
  { label: '无业人员', value: '70' },
  { label: '退（离）休人员', value: '80' },
  { label: '学生', value: '90' },
];

export const MRG_STAS_OPTIONS: FormItemOption[] = [
  { label: '未婚', value: '1' },
  { label: '已婚', value: '2' },
  { label: '丧偶', value: '3' },
  { label: '离婚', value: '4' },
  { label: '其他', value: '9' },
];

export const CONER_RLTS_OPTIONS: FormItemOption[] = [
  { label: '配偶', value: '1' },
  { label: '子', value: '2' },
  { label: '女', value: '3' },
  { label: '父母', value: '4' },
  { label: '兄弟姐妹', value: '5' },
  { label: '同事', value: '6' },
  { label: '其他', value: '9' },
];

/** 是否类（1是 2否） */
export const YES_NO_12_OPTIONS: FormItemOption[] = [
  { label: '是', value: '1' },
  { label: '否', value: '2' },
];

/** 有无类（1无 2有） */
export const NONE_HAS_OPTIONS: FormItemOption[] = [
  { label: '无', value: '1' },
  { label: '有', value: '2' },
];

export const TRT_TYPE_OPTIONS: FormItemOption[] = [
  { label: '中医', value: '1' },
  { label: '中西医', value: '2' },
  { label: '西医', value: '3' },
];

export const ABO_OPTIONS: FormItemOption[] = [
  { label: 'A', value: '1' },
  { label: 'B', value: '2' },
  { label: 'O', value: '3' },
  { label: 'AB', value: '4' },
  { label: '不详', value: '5' },
];

export const RH_OPTIONS: FormItemOption[] = [
  { label: '阴', value: '1' },
  { label: '阳', value: '2' },
  { label: '不详', value: '3' },
];

export const MEDCAS_QLT_OPTIONS: FormItemOption[] = [
  { label: '甲', value: '1' },
  { label: '乙', value: '2' },
  { label: '丙', value: '3' },
];

/** 医生可编辑状态 */
export const DOCTOR_EDITABLE_STATUS: CaseRecordStatusCode[] = ['draft', 'returned'];

/** 病案室可操作状态 */
export const RECORD_ROOM_RECEIVE_STATUS: CaseRecordStatusCode[] = ['submitted'];
export const RECORD_ROOM_RETURN_STATUS: CaseRecordStatusCode[] = ['submitted', 'received'];
