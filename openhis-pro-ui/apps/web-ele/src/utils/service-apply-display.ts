import type { ServiceRequestDetailSummaryDto } from '#/api/doctor-station/advice.types';

/** 检查行明细：身体部位 */
export const SERVICE_APPLY_BODY_STRUCTURE_TABLE = 'dia_body_structure';

/** 检验行明细：样本定义 */
export const SERVICE_APPLY_SPECIMEN_TABLE = 'adm_specimen_definition';

/**
 * 康复科治疗申请单固定疗程说明。
 * 匹配执行科室展示名含「康复科」或「康复医学科」。
 */
export const REHAB_TREATMENT_COURSE_REMARK = '疗程：每日一次，每次20分钟，7天为一疗程';

export interface ServiceApplyFormDisplayInput {
  remark?: null | string;
  serviceRequestDetails?: ServiceRequestDetailSummaryDto[] | null;
}

function trimDisplayText(value: unknown): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

/** 执行科室展示名是否为康复科（含康复医学科） */
export function isRehabPerformOrgDisplayName(performOrgName: null | string | undefined): boolean {
  const name = trimDisplayText(performOrgName);
  if (!name) {
    return false;
  }
  return name.includes('康复科') || name.includes('康复医学科');
}

/** 从行备注中去掉康复科固定疗程说明（切走执行科室时还原） */
export function stripRehabTreatmentCourseRemark(remarkText: null | string | undefined): string {
  const base = trimDisplayText(remarkText);
  if (!base || !base.includes(REHAB_TREATMENT_COURSE_REMARK)) {
    return base;
  }
  return base
    .split(/\n|；|;/)
    .map((part) => part.trim())
    .filter((part) => part.length > 0 && part !== REHAB_TREATMENT_COURSE_REMARK)
    .join('；');
}

/**
 * 单行备注：治疗申请 + 康复科执行科室时写入固定疗程说明；否则去掉该说明。
 * @param remarkText 当前行备注
 * @param applyKind 申请单类型
 * @param performOrgName 执行科室展示名
 * @param separator 已有备注与疗程说明的分隔符
 */
export function syncLineRemarkWithRehabTreatmentCourse(
  remarkText: null | string | undefined,
  applyKind: null | string | undefined,
  performOrgName: null | string | undefined,
  separator = '；',
): string | undefined {
  const withoutCourse = stripRehabTreatmentCourseRemark(remarkText);
  if (applyKind !== 'treatment' || !isRehabPerformOrgDisplayName(performOrgName)) {
    return withoutCourse || undefined;
  }
  if (!withoutCourse) {
    return REHAB_TREATMENT_COURSE_REMARK;
  }
  if (withoutCourse.includes(REHAB_TREATMENT_COURSE_REMARK)) {
    return withoutCourse;
  }
  return `${withoutCourse}${separator}${REHAB_TREATMENT_COURSE_REMARK}`;
}

/**
 * 治疗申请单备注/注意事项：执行科室为康复科时追加固定疗程说明。
 * @param remarkText 已有备注（行备注合并结果）
 * @param applyKind 申请单类型；仅 treatment 追加
 * @param performOrgName 执行科室展示名
 * @param separator 追加分隔符（预览用「；」，打印用换行）
 */
export function appendRehabTreatmentCourseRemark(
  remarkText: string,
  applyKind: null | string | undefined,
  performOrgName: null | string | undefined,
  separator = '\n',
): string {
  return (
    syncLineRemarkWithRehabTreatmentCourse(remarkText, applyKind, performOrgName, separator) ?? ''
  );
}

/** 单条部位/样本明细展示：名称 + 可选同行补充说明 */
function formatServiceRequestDetailItem(item: ServiceRequestDetailSummaryDto): string {
  const name = trimDisplayText(item.targetDisplayName);
  const detailRemark = trimDisplayText(item.remark);
  if (name && detailRemark) {
    return `${name}（${detailRemark}）`;
  }
  return name || detailRemark;
}

/** 申请单部位/样本摘要（样本：xx、yy） */
export function buildServiceApplyFormDetailHint(
  details: ServiceRequestDetailSummaryDto[] | null | undefined,
): string {
  if (!details?.length) {
    return '';
  }
  const table = trimDisplayText(details[0]?.targetTable);
  let label = '明细';
  if (table === SERVICE_APPLY_SPECIMEN_TABLE) {
    label = '样本';
  } else if (table === SERVICE_APPLY_BODY_STRUCTURE_TABLE) {
    label = '部位';
  }
  const parts = details.map(formatServiceRequestDetailItem).filter((part) => part.length > 0);
  if (parts.length === 0) {
    return '';
  }
  return `${label}：${parts.join('、')}`;
}

/** 申请单 extension 片段：样本/部位 + 行级备注（有值才返回） */
export function buildServiceApplyFormExtensionParts(input: ServiceApplyFormDisplayInput): string[] {
  const parts: string[] = [];
  const detailHint = buildServiceApplyFormDetailHint(input.serviceRequestDetails);
  if (detailHint) {
    parts.push(detailHint);
  }
  const lineRemark = trimDisplayText(input.remark);
  if (lineRemark) {
    parts.push(`备注：${lineRemark}`);
  }
  return parts;
}

/** 申请单 extension 单行文案（`;` 分隔各段） */
export function buildServiceApplyFormExtensionText(
  input: ServiceApplyFormDisplayInput,
  trailingParts: string[] = [],
): string {
  return [...buildServiceApplyFormExtensionParts(input), ...trailingParts]
    .filter((part) => part.length > 0)
    .join('；');
}
