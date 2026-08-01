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

/** 带患者上下文的组行草稿（右侧多选患者汇总表） */
export interface TraceAssignmentGroupDraftWithPatient extends TraceAssignmentGroupDraft {
  encounterId: string;
  patientName: string;
  bedName?: string;
}

/** 右侧分组表 · 药批数据行 */
export interface TraceAssignmentDisplayRow extends TraceAssignmentGroupDraftWithPatient {
  rowId: string;
  rowKind: 'body';
  isFirstInPatient: boolean;
  isLastInPatient: boolean;
  patientRowSpan: number;
}

/** 右侧分组表 · 患者组间隔行 */
export interface TraceAssignmentSpacerRow {
  rowId: string;
  rowKind: 'spacer';
  encounterId: string;
}

export type TraceAssignmentTableRow = TraceAssignmentDisplayRow | TraceAssignmentSpacerRow;

/** 是否为患者组间隔行 */
export function isTraceAssignmentSpacerRow(
  row: TraceAssignmentTableRow,
): row is TraceAssignmentSpacerRow {
  return row.rowKind === 'spacer';
}

/** 将「已选患者 → 草稿」展成分组表行（含间隔行）。
 * 床号/患者列由 span-method 合并，与门诊发药列表明细同构。
 * @param statusFilter 按分配状态过滤（待分配/改码场景）
 */
export function buildGroupedTraceAssignmentRows(
  selectedEncounterIds: string[],
  draftsByEncounterId: Record<string, TraceAssignmentGroupDraft[]>,
  patientMetaByEncounterId: Record<string, { patientName: string; bedName?: string }>,
  statusFilter?: 'ASSIGNED' | 'PENDING',
): TraceAssignmentTableRow[] {
  const rows: TraceAssignmentTableRow[] = [];
  let groupIndex = 0;
  for (const encounterId of selectedEncounterIds) {
    const allDrafts = draftsByEncounterId[encounterId] ?? [];
    const drafts = statusFilter
      ? allDrafts.filter((d) => String(d.group.assignmentStatus ?? '') === statusFilter)
      : allDrafts;
    if (drafts.length === 0) {
      continue;
    }
    if (groupIndex > 0) {
      rows.push({
        rowId: `spacer-${encounterId}`,
        rowKind: 'spacer',
        encounterId: '',
      });
    }
    const meta = patientMetaByEncounterId[encounterId];
    const patientName = meta?.patientName ?? '—';
    const bedName = meta?.bedName;
    const n = drafts.length;
    drafts.forEach((draft, i) => {
      const groupKey = String(draft.group.groupKey ?? i);
      rows.push({
        ...draft,
        encounterId,
        patientName,
        bedName,
        rowId: `${encounterId}:${groupKey}`,
        rowKind: 'body',
        isFirstInPatient: i === 0,
        isLastInPatient: i === n - 1,
        patientRowSpan: n,
      });
    });
    groupIndex += 1;
  }
  return rows;
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
