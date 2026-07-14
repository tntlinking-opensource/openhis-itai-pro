import type {
  PatientTraceAssignmentGroupDto,
  PatientTraceAssignmentGroupSubmitDto,
  SavePatientTraceAssignmentRequest,
  TraceCodeMatchPreviewDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-trace-assignment.types';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 组行草稿：页面编辑态追溯码 */
export interface TraceAssignmentGroupDraft {
  group: PatientTraceAssignmentGroupDto;
  traceNo: string;
}

/** 将接口组行转为可编辑草稿 */
export function mapGroupsToDrafts(
  groups: PatientTraceAssignmentGroupDto[],
): TraceAssignmentGroupDraft[] {
  return groups.map((group) => ({
    group,
    traceNo: String(group.assignedTraceNo ?? '').trim(),
  }));
}

/** 将匹配预览合并进草稿（按 groupKey 覆盖或追加逗号码） */
export function applyTraceMatchPreviews(
  drafts: TraceAssignmentGroupDraft[],
  matches: TraceCodeMatchPreviewDto[],
): TraceAssignmentGroupDraft[] {
  const matchByKey = new Map<string, TraceCodeMatchPreviewDto>();
  for (const match of matches) {
    const key = String(match.groupKey ?? '').trim();
    if (key) {
      matchByKey.set(key, match);
    }
  }
  return drafts.map((draft) => {
    const key = String(draft.group.groupKey ?? '').trim();
    const match = matchByKey.get(key);
    if (!match?.matchedTraceCodes?.length) {
      return draft;
    }
    return {
      ...draft,
      traceNo: match.matchedTraceCodes.join(','),
    };
  });
}

/** 格式化发药量展示 */
export function formatTraceAssignmentQuantity(group: PatientTraceAssignmentGroupDto): string {
  const qty = group.dispenseQuantitySplit;
  const unit = group.splitUnitCode_dictText ?? group.splitUnitCode ?? '';
  if (qty === undefined || qty === null || qty === '') {
    return unit ? `— ${unit}` : '—';
  }
  return unit ? `${qty} ${unit}` : String(qty);
}

/** 解析失败原因展示 */
export function resolveTraceFailReasonLabel(reasonCode?: string): string {
  switch (reasonCode) {
    case 'INVALID':
      return '格式非法';
    case 'NOT_IN_POOL':
      return '不在汇总码池';
    case 'NOT_MATCHED':
      return '无法匹配药品批号';
    case 'OVER_LIMIT':
      return '超过发药数量';
    default:
      return reasonCode ? String(reasonCode) : '解析失败';
  }
}

/** 构建保存请求体 */
export function buildSavePatientTraceAssignmentRequest(
  encounterId: string,
  drafts: TraceAssignmentGroupDraft[],
): SavePatientTraceAssignmentRequest {
  const groups: PatientTraceAssignmentGroupSubmitDto[] = [];
  for (const draft of drafts) {
    const groupKey = String(draft.group.groupKey ?? '').trim();
    if (!groupKey) {
      continue;
    }
    const dispenseIds = (draft.group.dispenseIds ?? [])
      .map((id) => preserveApiIdForRequest(id))
      .filter((id): id is string => Boolean(id));
    if (dispenseIds.length === 0) {
      continue;
    }
    groups.push({
      groupKey,
      dispenseIds,
      traceNo: String(draft.traceNo ?? '').trim(),
    });
  }
  return { encounterId, groups };
}

/** 收集当前组键快照（批量解析用） */
export function collectGroupKeys(drafts: TraceAssignmentGroupDraft[]): string[] {
  const keys: string[] = [];
  for (const draft of drafts) {
    const key = String(draft.group.groupKey ?? '').trim();
    if (key) {
      keys.push(key);
    }
  }
  return keys;
}

/** 是否存在待保存的非空追溯码 */
export function hasTraceAssignmentChanges(drafts: TraceAssignmentGroupDraft[]): boolean {
  return drafts.some((draft) => String(draft.traceNo ?? '').trim().length > 0);
}
