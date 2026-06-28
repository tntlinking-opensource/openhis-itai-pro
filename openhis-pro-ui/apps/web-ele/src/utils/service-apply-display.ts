import type { ServiceRequestDetailSummaryDto } from '#/api/doctor-station/advice.types';

/** 检查行明细：身体部位 */
export const SERVICE_APPLY_BODY_STRUCTURE_TABLE = 'dia_body_structure';

/** 检验行明细：样本定义 */
export const SERVICE_APPLY_SPECIMEN_TABLE = 'adm_specimen_definition';

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
