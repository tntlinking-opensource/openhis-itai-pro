import type {
  DoctorStationAdviceDraftBindItemDto,
  DoctorStationAdviceDraftLineRequest,
} from '#/api/doctor-station/advice.types';

import { MEDICATION_DEFINITION_TABLE } from './advice-tab.constants';

export const USAGE_BIND_TYPE = 'USAGE';
export const ACTIVITY_BIND_TYPE = 'ACTIVITY';

/** 绑定来源键：USAGE|{methodCode} / ACTIVITY|{activityDefinitionId} */
export type AdviceBindSourceCacheKey = string;

/** 绑定来源类型展示：用法 / 诊疗 */
export function resolveBindSourceKindLabel(
  bindType: string | undefined,
  sourceCacheKey?: string,
): string {
  const type = String(bindType ?? '').trim();
  if (type === USAGE_BIND_TYPE) {
    return '用法';
  }
  if (type === ACTIVITY_BIND_TYPE) {
    return '诊疗';
  }
  const key = String(sourceCacheKey ?? '').trim();
  if (key.startsWith(`${USAGE_BIND_TYPE}|`)) {
    return '用法';
  }
  if (key.startsWith(`${ACTIVITY_BIND_TYPE}|`)) {
    return '诊疗';
  }
  return '';
}

/** IF-DOR-23 绑定项 → 来源键 */
export function resolveBindSourceCacheKey(
  item: DoctorStationAdviceDraftBindItemDto,
  lines: DoctorStationAdviceDraftLineRequest[],
): AdviceBindSourceCacheKey | undefined {
  const bindType = String(item.bindType ?? '').trim();
  const lineIndex = item.triggerLineIndex;
  if (lineIndex === undefined || lineIndex === null) {
    return undefined;
  }
  const line = lines[lineIndex];
  if (!line) {
    return undefined;
  }

  if (bindType === USAGE_BIND_TYPE) {
    const productTableName = String(line.productTableName ?? '').trim();
    if (productTableName !== MEDICATION_DEFINITION_TABLE) {
      return undefined;
    }
    const methodCode = String(line.medicationPayload?.methodCode ?? '').trim();
    return methodCode ? `${USAGE_BIND_TYPE}|${methodCode}` : undefined;
  }

  if (bindType === ACTIVITY_BIND_TYPE) {
    const activityDefinitionId = String(line.servicePayload?.activityDefinitionId ?? '').trim();
    return activityDefinitionId ? `${ACTIVITY_BIND_TYPE}|${activityDefinitionId}` : undefined;
  }

  return undefined;
}
