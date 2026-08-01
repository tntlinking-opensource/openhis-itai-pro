import type { ServiceApplyKind } from './service-apply.types';

/** 申请单选品目录表名 */
export const SERVICE_APPLY_ACTIVITY_TABLE = 'wkf_activity_definition';

/** 检查行明细：身体部位 */
export const SERVICE_APPLY_BODY_STRUCTURE_TABLE = 'dia_body_structure';

/** 检验行明细：样本定义 */
export const SERVICE_APPLY_SPECIMEN_TABLE = 'adm_specimen_definition';

/** 签发批次：诊疗定义表名（与列表/暂存锚点一致） */
export const SERVICE_APPLY_SIGN_PRODUCT_TABLE = 'wkf_activity_definition';

/** 申请单类别 code：检验 PROOF/22、检查 TEST/23、治疗 TREATMENT/21 */
export const SERVICE_APPLY_CATEGORY_BY_KIND: Record<ServiceApplyKind, string> = {
  lab: 'PROOF',
  exam: 'TEST',
  treatment: 'TREATMENT',
};

/** 申请单类型短文案 */
export const SERVICE_APPLY_KIND_LABEL: Record<ServiceApplyKind, string> = {
  lab: '检验',
  exam: '检查',
  treatment: '治疗',
};

/** 目录分页默认页大小 */
export const SERVICE_APPLY_CATALOG_PAGE_SIZE = 50;

/** 检查须填部位；检验样本与治疗部位可选（BR-REQ-005） */
export function isServiceApplyDetailRequired(kind: ServiceApplyKind): boolean {
  return kind === 'exam';
}
