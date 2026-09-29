import type { FormItemOption } from '@vben-core/table-layout-ui';

import type { CaseRecordStatusCode } from '#/api/mrms/types';

/** 病案状态展示（仅归档三态；质控另开） */
export const CASE_RECORD_STATUS_OPTIONS: FormItemOption[] = [
  { label: '草稿', value: 'DRAFT' },
  { label: '待归档审批', value: 'PENDING_ARCHIVE' },
  { label: '已归档', value: 'ARCHIVED' },
];

export const CASE_RECORD_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_ARCHIVE: '待归档审批',
  ARCHIVED: '已归档',
};

export const CASE_RECORD_STATUS_TAG: Record<string, 'danger' | 'info' | 'success' | 'warning'> = {
  DRAFT: 'info',
  PENDING_ARCHIVE: 'warning',
  ARCHIVED: 'success',
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

/** 诊断子表分组（对齐老 EMR 入院/出院/底栏结构） */
export type HomepageDiagGroup = 'admission' | 'discharge' | 'special';

export const HOMEPAGE_DIAG_GROUP_TYPES: Record<HomepageDiagGroup, HomepageDiagTypeCode[]> = {
  admission: ['admission_main', 'admission_other'],
  discharge: ['discharge_main', 'discharge_other'],
  special: ['pathology', 'injury_poison'],
};

/** 是否上传医保（对齐老 needYbUp：1是 0否） */
export const NEED_YB_UP_OPTIONS: FormItemOption[] = [
  { label: '是', value: '1' },
  { label: '否', value: '0' },
];

/** 主要诊断治愈好转（老 cureStatus） */
export const MAIN_DIAG_CURE_OPTIONS: FormItemOption[] = [
  { label: '1.治愈', value: '1' },
  { label: '2.好转', value: '2' },
  { label: '3.未愈', value: '3' },
];

/** 临床路径管理（老 clinicPathStatus，异于附页「是否实施」） */
export const CLINIC_PATH_MANAGE_OPTIONS: FormItemOption[] = [
  { label: '1.完成', value: '1' },
  { label: '2.变异', value: '2' },
  { label: '3.退出', value: '3' },
  { label: '4.未入', value: '4' },
];

/** 诊断符合情况码（0未做 1符合 2不符合 3不确定） */
export const DIAG_MATCH_OPTIONS: FormItemOption[] = [
  { label: '0.未做', value: '0' },
  { label: '1.符合', value: '1' },
  { label: '2.不符合', value: '2' },
  { label: '3.不确定', value: '3' },
];

/** 左侧锚点 / 主分块（与首页 Tab 一致） */
export const HOMEPAGE_ANCHOR_SECTIONS = [
  { id: 'sec-inpatient', label: '住院信息', section: 'basic' as const },
  { id: 'sec-diagnosis', label: '诊断记录', section: 'diagnosis' as const },
  { id: 'sec-fee', label: '费用结算', section: 'fee' as const },
  { id: 'sec-procedure', label: '手术/操作记录', section: 'procedure' as const },
  { id: 'sec-appendix', label: '病案附页', section: 'staff' as const },
];

/** 病案费用大类（对齐老 EMR_SETTLE_ITEM.SETTLE_ITEM_TYPE） */
export type HomepageFeeCategory =
  | '综合医疗服务类'
  | '诊断类'
  | '治疗类'
  | '康复类'
  | '中医类'
  | '西药类'
  | '中药类'
  | '血液和血液制品类'
  | '耗材类'
  | '其他类';

/** 病案结算类型 → 费用分项（带大类，供费用结算表按类合并展示） */
export const HOMEPAGE_FEE_ITEM_OPTIONS: {
  key: string;
  label: string;
  category: HomepageFeeCategory;
}[] = [
  { key: 'ordn_med_servfee', label: '一般医疗服务费', category: '综合医疗服务类' },
  { key: 'ordn_trt_oprt_fee', label: '一般治疗操作费', category: '综合医疗服务类' },
  { key: 'nurs_fee', label: '护理费', category: '综合医疗服务类' },
  { key: 'com_med_serv_oth_fee', label: '综合医疗服务类其他费用', category: '综合医疗服务类' },
  { key: 'palg_diag_fee', label: '病理诊断费', category: '诊断类' },
  { key: 'lab_diag_fee', label: '实验室诊断费', category: '诊断类' },
  { key: 'rdhy_diag_fee', label: '影像学诊断费', category: '诊断类' },
  { key: 'clnc_dise_fee', label: '临床诊断项目费', category: '诊断类' },
  { key: 'nsrgtrt_item_fee', label: '非手术治疗项目费', category: '治疗类' },
  { key: 'clnc_phys_trt_fee', label: '临床物理治疗费', category: '治疗类' },
  { key: 'rgtrt_trt_fee', label: '手术治疗费', category: '治疗类' },
  { key: 'anst_fee', label: '麻醉费', category: '治疗类' },
  { key: 'rgtrt_fee', label: '手术费', category: '治疗类' },
  { key: 'rhab_fee', label: '康复费', category: '康复类' },
  { key: 'tcm_trt_fee', label: '中医治疗费', category: '中医类' },
  { key: 'wm_fee', label: '西药费', category: '西药类' },
  { key: 'abtl_medn_fee', label: '抗菌药物费用', category: '西药类' },
  { key: 'tcmpat_fee', label: '中成药费', category: '中药类' },
  { key: 'tcmherb_fee', label: '中药饮片费', category: '中药类' },
  { key: 'blo_fee', label: '血费', category: '血液和血液制品类' },
  { key: 'albu_fee', label: '白蛋白类制品费', category: '血液和血液制品类' },
  { key: 'glon_fee', label: '球蛋白类制品费', category: '血液和血液制品类' },
  { key: 'clotfac_fee', label: '凝血因子类制品费', category: '血液和血液制品类' },
  { key: 'cyki_fee', label: '细胞因子类制品费', category: '血液和血液制品类' },
  { key: 'exam_dspo_matl_fee', label: '检查用一次性医用材料费', category: '耗材类' },
  { key: 'trt_dspo_matl_fee', label: '治疗用一次性医用材料费', category: '耗材类' },
  { key: 'oprn_dspo_matl_fee', label: '手术用一次性医用材料费', category: '耗材类' },
  { key: 'oth_fee', label: '其他费', category: '其他类' },
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

/**
 * 诊断转归/病情（老系统 outIllStatus；落库字段 outIllStatusCode）。
 * 1治愈 2好转 3未愈 4死亡 9其他
 */
export const OUT_ILL_STATUS_OPTIONS: FormItemOption[] = [
  { label: '治愈', value: '1' },
  { label: '好转', value: '2' },
  { label: '未愈', value: '3' },
  { label: '死亡', value: '4' },
  { label: '其他', value: '9' },
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

/**
 * 实施临床路径（国标中医病案首页：1中医 2西医 3否；老 inpath）。
 */
export const CLINICAL_PATH_OPTIONS: FormItemOption[] = [
  { label: '中医', value: '1' },
  { label: '西医', value: '2' },
  { label: '否', value: '3' },
];

/** 有无类（1无 2有） */
export const NONE_HAS_OPTIONS: FormItemOption[] = [
  { label: '无', value: '1' },
  { label: '有', value: '2' },
];

/** 治疗类别（国标中医病案首页 / 4401 trt_type：1西医 2中医 2.1中医 2.2民族医 3中西医） */
export const TRT_TYPE_OPTIONS: FormItemOption[] = [
  { label: '1.西医', value: '1' },
  { label: '2.中医', value: '2' },
  { label: '2.1中医', value: '2.1' },
  { label: '2.2民族医', value: '2.2' },
  { label: '3.中西医', value: '3' },
];

/** ABO：对齐医保 blotype_abo（V1.6.8），未查=9（勿用老 HIS 提示里的 6） */
export const ABO_OPTIONS: FormItemOption[] = [
  { label: 'A', value: '1' },
  { label: 'B', value: '2' },
  { label: 'O', value: '3' },
  { label: 'AB', value: '4' },
  { label: '不详', value: '5' },
  { label: '未查', value: '9' },
];

/** Rh：对齐医保 blotype_rh（V1.6.8），未查=4 */
export const RH_OPTIONS: FormItemOption[] = [
  { label: '阴', value: '1' },
  { label: '阳', value: '2' },
  { label: '不详', value: '3' },
  { label: '未查', value: '4' },
];

export const MEDCAS_QLT_OPTIONS: FormItemOption[] = [
  { label: '甲', value: '1' },
  { label: '乙', value: '2' },
  { label: '丙', value: '3' },
];

/** 医生可编辑状态（仅草稿；归档后走编辑申请） */
export const DOCTOR_EDITABLE_STATUS: CaseRecordStatusCode[] = ['DRAFT'];

/** @deprecated 收案流已拆出；保留空数组避免旧引用编译失败 */
export const RECORD_ROOM_RECEIVE_STATUS: CaseRecordStatusCode[] = [];
/** @deprecated 退回流已拆出 */
export const RECORD_ROOM_RETURN_STATUS: CaseRecordStatusCode[] = [];

/**
 * 拼病案相关页签标题（前缀-姓名-住院号，缺省段省略）。
 *
 * @param prefix 业务前缀，如「结算清单」「病案详情」
 * @param patientName 患者姓名
 * @param iptNo 住院号
 */
export function buildMrmsPatientTabTitle(
  prefix: string,
  patientName?: string,
  iptNo?: string,
): string {
  const name = (patientName ?? '').trim();
  const ipt = (iptNo ?? '').trim();
  if (name && ipt) {
    return `${prefix}-${name}-${ipt}`;
  }
  if (name) {
    return `${prefix}-${name}`;
  }
  if (ipt) {
    return `${prefix}-${ipt}`;
  }
  return prefix;
}
