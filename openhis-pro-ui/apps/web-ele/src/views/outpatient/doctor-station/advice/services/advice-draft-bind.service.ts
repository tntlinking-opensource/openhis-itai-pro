/**
 * IF-DOR-23 用法/诊疗绑定解析与落库。
 *
 * 仅供 advice-draft-save.service 中 MANUAL_USAGE_BIND 策略调用；
 * 仅新建药品（CREATE）触发用法绑定弹窗；UPDATE 编辑保存不二次带出。
 */
import type {
  AdviceDraftBindDialogRow,
  AdviceDraftBindDialogSourceGroup,
} from './advice-draft-bind.types';
import type { AdviceRowItem } from './advice.types';

import type {
  DoctorStationAdviceDraftBindItemDto,
  DoctorStationAdviceDraftLineRequest,
  DoctorStationAdviceRowDto,
  SaveDoctorStationAdviceDraftBatchResponse,
} from '#/api/doctor-station/advice.types';

import {
  httpListDoctorStationAdvice,
  httpResolveDoctorStationAdviceDraftBinds,
  httpSaveDoctorStationAdviceDraftBatch,
} from '#/api/doctor-station/advice.http';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  addAdviceBindSkipSources,
  isAdviceBindSourceSkipped,
  resolveAdviceBindSkipCacheUserId,
  resolveBindSourceCacheKey,
  resolveBindSourceKindLabel,
  USAGE_BIND_TYPE,
} from './advice-bind-skip-cache.service';
import { openAdviceDraftBindDialog } from './advice-draft-bind-dialog.controller';
import {
  ACTIVITY_DEFINITION_TABLE,
  DEVICE_DEFINITION_TABLE,
  MEDICATION_DEFINITION_TABLE,
  MEDICATION_REQUEST_TABLE,
  SERVICE_REQUEST_TABLE,
} from './advice-tab.constants';

export interface SaveAdviceDraftBatchWithBindOptions {
  encounterId: string;
  patientId: string;
  lines: DoctorStationAdviceDraftLineRequest[];
  /** 当前就诊已有医嘱行，用于去重与 CREATE 后锚点解析 */
  existingAdviceRows?: AdviceRowItem[] | DoctorStationAdviceRowDto[];
  pricingSourceCode?: string;
}

type AdviceRowLike = AdviceRowItem | DoctorStationAdviceRowDto;

/** 列表/快照行是否为诊疗（定义表或 resourceType 回退） */
function isServiceAdviceRow(row: AdviceRowLike): boolean {
  const pt = String(row.productTableName ?? '').trim();
  if (pt === ACTIVITY_DEFINITION_TABLE) {
    return true;
  }
  const rt = String(row.resourceType ?? '')
    .trim()
    .toUpperCase();
  return rt === 'SERVICE';
}

/** 解析绑定数量：取模板默认分子，不乘主医嘱数量（BR-ORD-029） */
function resolveBindQuantity(item: DoctorStationAdviceDraftBindItemDto): number {
  const raw = Number(item.quantityNumerator ?? 1);
  if (Number.isFinite(raw) && raw > 0) {
    return raw;
  }
  return 1;
}

/** 解析变更行中药品成组号 */
function resolveMedicationLineGroupId(
  lineIndex: null | number | undefined,
  lines: DoctorStationAdviceDraftLineRequest[],
): string | undefined {
  if (lineIndex === undefined || lineIndex === null) {
    return undefined;
  }
  const line = lines[lineIndex];
  if (!line) {
    return undefined;
  }
  const productTableName = String(line.productTableName ?? '').trim();
  if (productTableName !== MEDICATION_DEFINITION_TABLE) {
    return undefined;
  }
  return String(line.medicationPayload?.groupId ?? '').trim() || undefined;
}

/** 解析变更行中药品用法编码 */
function resolveMedicationLineMethodCode(
  lineIndex: null | number | undefined,
  lines: DoctorStationAdviceDraftLineRequest[],
): string | undefined {
  if (lineIndex === undefined || lineIndex === null) {
    return undefined;
  }
  const line = lines[lineIndex];
  if (!line) {
    return undefined;
  }
  return String(line.medicationPayload?.methodCode ?? '').trim() || undefined;
}

/** 列表行是否为与绑定锚点匹配的派生行（手动开立行不参与去重） */
function isBindDerivedRowForAnchor(row: AdviceRowLike, basedOnTable: string): boolean {
  if (basedOnTable === MEDICATION_REQUEST_TABLE) {
    return Boolean(String(row.basedOnMedicationRequestId ?? '').trim());
  }
  if (basedOnTable === SERVICE_REQUEST_TABLE) {
    return Boolean(String(row.basedOnServiceRequestId ?? '').trim());
  }
  return false;
}

/** 绑定存在性去重：仅待签发草稿参与；已签发行不参与（签发后自动移出去重池） */
function rowEligibleForBindExistenceDedupe(row: AdviceRowLike): boolean {
  const code = String(row.statusEnum ?? '')
    .trim()
    .toUpperCase();
  if (code === 'DRAFT') {
    return true;
  }
  const statusText = String(
    ('status' in row && row.status !== undefined ? row.status : undefined) ??
      ('displayStatusEnumText' in row ? row.displayStatusEnumText : undefined) ??
      ('statusEnumText' in row ? row.statusEnumText : undefined) ??
      '',
  ).trim();
  const blob = `${statusText} ${row.statusEnum ?? ''}`.toUpperCase();
  return /待签发|草稿|DRAFT|暂存|新开/.test(blob);
}

/** 列表行是否为药品（定义表） */
function isMedicationAdviceRow(row: AdviceRowLike): boolean {
  const pt = String(row.productTableName ?? '').trim();
  if (pt === MEDICATION_DEFINITION_TABLE) {
    return true;
  }
  const rt = String(row.resourceType ?? '')
    .trim()
    .toUpperCase();
  return rt === 'MEDICATION';
}

/** USAGE 组级上下文：同组同用法 */
function resolveUsageGroupBindContext(
  item: DoctorStationAdviceDraftBindItemDto,
  lines: DoctorStationAdviceDraftLineRequest[],
): undefined | { groupId: string; methodCode: string } {
  if (String(item.bindType ?? '').trim() !== USAGE_BIND_TYPE) {
    return undefined;
  }
  const groupId = resolveMedicationLineGroupId(item.triggerLineIndex, lines);
  const methodCode =
    resolveMedicationLineMethodCode(item.triggerLineIndex, lines) ||
    String(item.sourceItemId ?? '').trim();
  if (!groupId || !methodCode) {
    return undefined;
  }
  return { groupId, methodCode };
}

/** 待签发池内：同组同用法药品 MR id（列表 + 当前变更行） */
function collectDraftMedicationRequestIdsForUsageGroup(
  groupId: string,
  methodCode: string,
  existingRows: AdviceRowLike[],
  lines: DoctorStationAdviceDraftLineRequest[],
): Set<string> {
  const ids = new Set<string>();
  for (const row of existingRows) {
    if (!rowEligibleForBindExistenceDedupe(row) || !isMedicationAdviceRow(row)) {
      continue;
    }
    if (String(row.groupId ?? '').trim() !== groupId) {
      continue;
    }
    if (String(row.methodCode ?? '').trim() !== methodCode) {
      continue;
    }
    const rid = preserveApiIdForRequest(row.requestId);
    if (rid) {
      ids.add(rid);
    }
  }
  for (const line of lines) {
    if (line.lineOp === 'DELETE') {
      continue;
    }
    const pt = String(line.productTableName ?? '').trim();
    if (pt !== MEDICATION_DEFINITION_TABLE) {
      continue;
    }
    const gid = String(line.medicationPayload?.groupId ?? '').trim();
    if (gid !== groupId) {
      continue;
    }
    const mc = String(line.medicationPayload?.methodCode ?? '').trim();
    if (mc !== methodCode) {
      continue;
    }
    const rid = preserveApiIdForRequest(line.requestId);
    if (rid) {
      ids.add(rid);
    }
  }
  return ids;
}

/** 列表派生行是否匹配绑定目标定义 */
function listRowMatchesBindTarget(
  row: AdviceRowLike,
  targetId: string,
  targetTable: string,
): boolean {
  const defId = String(row.productDefinitionId ?? '').trim();
  const pt = String(row.productTableName ?? '').trim();
  return defId === targetId && pt === targetTable;
}

/** 生成绑定去重键：仅同组药品同用法合并；其余必须目标+锚点完全一致才去重 */
function buildBindDedupeKey(
  item: DoctorStationAdviceDraftBindItemDto,
  lines?: DoctorStationAdviceDraftLineRequest[],
): string {
  const targetTable = String(item.targetProductTableName ?? '').trim();
  const targetId = String(item.targetItemId ?? '').trim();
  const basedOnTable = String(item.basedOnTable ?? '').trim();
  const basedOnId =
    preserveApiIdForRequest(item.basedOnRequestId) ??
    preserveApiIdForRequest(item.triggerRequestId) ??
    `line:${item.triggerLineIndex ?? -1}`;

  if (lines && String(item.bindType ?? '').trim() === USAGE_BIND_TYPE) {
    const groupId = resolveMedicationLineGroupId(item.triggerLineIndex, lines);
    const methodCode =
      resolveMedicationLineMethodCode(item.triggerLineIndex, lines) ||
      String(item.sourceItemId ?? '').trim();
    if (groupId && methodCode) {
      return `${targetTable}|${targetId}|group:${groupId}|usage:${methodCode}`;
    }
  }

  return `${targetTable}|${targetId}|${basedOnTable}|${basedOnId}`;
}

/** 变更行是否为新建药品（仅 CREATE 药品触发用法绑定带出） */
function isMedicationCreateDraftLine(line: DoctorStationAdviceDraftLineRequest): boolean {
  if (line.lineOp !== 'CREATE') {
    return false;
  }
  const productTableName = String(line.productTableName ?? '').trim();
  return productTableName === MEDICATION_DEFINITION_TABLE;
}

/** 批次内是否含新建药品行 */
function hasMedicationCreateLine(lines: readonly DoctorStationAdviceDraftLineRequest[]): boolean {
  return lines.some(isMedicationCreateDraftLine);
}

/** 绑定项是否由新建药品行触发（UPDATE 编辑保存不参与二次带出） */
function isBindItemTriggeredByMedicationCreate(
  item: DoctorStationAdviceDraftBindItemDto,
  lines: readonly DoctorStationAdviceDraftLineRequest[],
): boolean {
  const lineIndex = item.triggerLineIndex;
  if (lineIndex === undefined || lineIndex === null) {
    return false;
  }
  const triggerLine = lines[lineIndex];
  if (!triggerLine) {
    return false;
  }
  return isMedicationCreateDraftLine(triggerLine);
}

/** 服务端不去重，前端仅合并：① 同组药品同用法同目标；② 目标+锚点完全相同的重复项 */
function dedupeAdviceDraftBindItems(
  bindItems: DoctorStationAdviceDraftBindItemDto[],
  lines: DoctorStationAdviceDraftLineRequest[] = [],
): DoctorStationAdviceDraftBindItemDto[] {
  const seen = new Set<string>();
  const result: DoctorStationAdviceDraftBindItemDto[] = [];
  const sorted = [...bindItems].toSorted(
    (a, b) =>
      (a.triggerLineIndex ?? Number.MAX_SAFE_INTEGER) -
      (b.triggerLineIndex ?? Number.MAX_SAFE_INTEGER),
  );
  for (const item of sorted) {
    const key = buildBindDedupeKey(item, lines);
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    result.push(item);
  }
  return result;
}

/** 绑定目标表 → 展示类型 */
function resolveTargetKindLabel(targetProductTableName: string): string {
  if (targetProductTableName === ACTIVITY_DEFINITION_TABLE) {
    return '诊疗';
  }
  if (targetProductTableName === DEVICE_DEFINITION_TABLE) {
    return '耗材';
  }
  return '项目';
}

/** IF-DOR-23 响应 → 弹窗可编辑行 */
function toAdviceDraftBindDialogRows(
  bindItems: DoctorStationAdviceDraftBindItemDto[],
  lines: DoctorStationAdviceDraftLineRequest[] = [],
): AdviceDraftBindDialogRow[] {
  return bindItems.map((item) => {
    const targetTable = String(item.targetProductTableName ?? '').trim();
    const sourceCacheKey = resolveBindSourceCacheKey(item, lines) ?? '';
    const bindType = String(item.bindType ?? '').trim();
    return {
      key: buildBindDedupeKey(item, lines),
      bindItem: item,
      sourceItemName: String(item.sourceItemName ?? item.bindType ?? '主医嘱').trim(),
      sourceCacheKey,
      sourceKindLabel: resolveBindSourceKindLabel(bindType, sourceCacheKey),
      targetItemName: String(item.targetItemName ?? item.targetItemId ?? '绑定项').trim(),
      targetKindLabel: resolveTargetKindLabel(targetTable),
      quantity: resolveBindQuantity(item),
      unitCode: String(item.unitCode ?? '').trim() || 'U',
      unitName: String(item.unitName ?? item.unitCode ?? '').trim(),
    };
  });
}

/**
 * 弹窗展示：USAGE 同目标同用法跨组合并为一行；确认后按 fanOutBindItems 按组展开落库。
 */
function mergeUsageDialogRowsAcrossGroups(
  rows: AdviceDraftBindDialogRow[],
  lines: DoctorStationAdviceDraftLineRequest[],
): AdviceDraftBindDialogRow[] {
  const merged: AdviceDraftBindDialogRow[] = [];
  const usageMergeIndexByKey = new Map<string, number>();

  for (const row of rows) {
    const bindType = String(row.bindItem.bindType ?? '').trim();
    if (bindType !== USAGE_BIND_TYPE) {
      merged.push(row);
      continue;
    }

    const targetTable = String(row.bindItem.targetProductTableName ?? '').trim();
    const targetId = String(row.bindItem.targetItemId ?? '').trim();
    const methodCode =
      resolveMedicationLineMethodCode(row.bindItem.triggerLineIndex, lines) ||
      String(row.bindItem.sourceItemId ?? '').trim();
    if (!targetTable || !targetId || !methodCode) {
      merged.push(row);
      continue;
    }

    // 跨组展示键：不含 groupId
    const mergeKey = `${targetTable}|${targetId}|usage:${methodCode}`;
    const existingIndex = usageMergeIndexByKey.get(mergeKey);
    if (existingIndex === undefined) {
      usageMergeIndexByKey.set(mergeKey, merged.length);
      merged.push({
        ...row,
        key: mergeKey,
        fanOutBindItems: [row.bindItem],
      });
      continue;
    }

    const existing = merged[existingIndex];
    if (!existing) {
      merged.push(row);
      continue;
    }
    const fanOut = [...(existing.fanOutBindItems ?? [existing.bindItem]), row.bindItem];
    const groupCount = fanOut.length;
    merged[existingIndex] = {
      ...existing,
      fanOutBindItems: fanOut,
      sourceItemName:
        groupCount > 1 ? `${existing.sourceItemName}（${groupCount} 组）` : existing.sourceItemName,
    };
  }

  return merged;
}

/**
 * 弹窗行展开为待落库绑定项：有 fanOut 则按组展开，否则仅 bindItem。
 */
function resolveDialogRowBindItemsForPersist(
  dialogRow: AdviceDraftBindDialogRow,
): DoctorStationAdviceDraftBindItemDto[] {
  if (dialogRow.fanOutBindItems && dialogRow.fanOutBindItems.length > 0) {
    return dialogRow.fanOutBindItems;
  }
  return [dialogRow.bindItem];
}

/**
 * 待签发列表中是否已有同规则派生行（手动开立同名项不算重复；已签发不参与）。
 * USAGE 同组同用法按组级语义；ACTIVITY 按 SR 锚点。
 */
function bindItemAlreadyExistsInAdviceList(
  item: DoctorStationAdviceDraftBindItemDto,
  basedOnRequestId: string | undefined,
  existingRows: AdviceRowLike[],
  lines: DoctorStationAdviceDraftLineRequest[],
): boolean {
  const targetId = String(item.targetItemId ?? '').trim();
  const targetTable = String(item.targetProductTableName ?? '').trim();
  const basedOnTable = String(item.basedOnTable ?? '').trim();
  if (!targetId || !targetTable || !basedOnTable) {
    return false;
  }

  const usageGroup = resolveUsageGroupBindContext(item, lines);
  if (usageGroup && basedOnTable === MEDICATION_REQUEST_TABLE) {
    const groupMrIds = collectDraftMedicationRequestIdsForUsageGroup(
      usageGroup.groupId,
      usageGroup.methodCode,
      existingRows,
      lines,
    );
    if (basedOnRequestId) {
      groupMrIds.add(basedOnRequestId);
    }
    if (groupMrIds.size === 0) {
      return false;
    }
    return existingRows.some((row) => {
      if (!rowEligibleForBindExistenceDedupe(row)) {
        return false;
      }
      if (!listRowMatchesBindTarget(row, targetId, targetTable)) {
        return false;
      }
      if (!isBindDerivedRowForAnchor(row, basedOnTable)) {
        return false;
      }
      const anchorId = preserveApiIdForRequest(row.basedOnMedicationRequestId);
      return Boolean(anchorId && groupMrIds.has(anchorId));
    });
  }

  if (!basedOnRequestId) {
    return false;
  }

  return existingRows.some((row) => {
    if (!rowEligibleForBindExistenceDedupe(row)) {
      return false;
    }
    if (!listRowMatchesBindTarget(row, targetId, targetTable)) {
      return false;
    }
    if (!isBindDerivedRowForAnchor(row, basedOnTable)) {
      return false;
    }
    if (basedOnTable === MEDICATION_REQUEST_TABLE) {
      return String(row.basedOnMedicationRequestId ?? '').trim() === basedOnRequestId;
    }
    if (basedOnTable === SERVICE_REQUEST_TABLE) {
      return String(row.basedOnServiceRequestId ?? '').trim() === basedOnRequestId;
    }
    return false;
  });
}

/** 当前暂存批次内是否已有同规则派生行（USAGE 同组同用法组级；其余按锚点） */
function bindItemAlreadyExistsInDraftLines(
  item: DoctorStationAdviceDraftBindItemDto,
  basedOnRequestId: string | undefined,
  lines: DoctorStationAdviceDraftLineRequest[],
  existingRows: AdviceRowLike[] = [],
): boolean {
  const targetId = String(item.targetItemId ?? '').trim();
  const targetTable = String(item.targetProductTableName ?? '').trim();
  const basedOnTable = String(item.basedOnTable ?? '').trim();
  if (!targetId || !targetTable || !basedOnTable) {
    return false;
  }

  const usageGroup = resolveUsageGroupBindContext(item, lines);
  if (usageGroup && basedOnTable === MEDICATION_REQUEST_TABLE) {
    const groupMrIds = collectDraftMedicationRequestIdsForUsageGroup(
      usageGroup.groupId,
      usageGroup.methodCode,
      existingRows,
      lines,
    );
    if (basedOnRequestId) {
      groupMrIds.add(basedOnRequestId);
    }
    if (groupMrIds.size === 0) {
      return false;
    }
    return lines.some((line) => {
      if (line.lineOp === 'DELETE') {
        return false;
      }
      const lineBasedOnTable = String(line.basedOnTable ?? '').trim();
      const lineBasedOnId = preserveApiIdForRequest(line.basedOnId);
      if (lineBasedOnTable !== basedOnTable || !lineBasedOnId || !groupMrIds.has(lineBasedOnId)) {
        return false;
      }
      const pt = String(line.productTableName ?? '').trim();
      if (pt === ACTIVITY_DEFINITION_TABLE) {
        return String(line.servicePayload?.activityDefinitionId ?? '').trim() === targetId;
      }
      if (pt === DEVICE_DEFINITION_TABLE) {
        return String(line.devicePayload?.deviceDefinitionId ?? '').trim() === targetId;
      }
      return false;
    });
  }

  if (!basedOnRequestId) {
    return false;
  }

  return lines.some((line) => {
    if (line.lineOp === 'DELETE') {
      return false;
    }
    const lineBasedOnTable = String(line.basedOnTable ?? '').trim();
    const lineBasedOnId = preserveApiIdForRequest(line.basedOnId);
    if (lineBasedOnTable !== basedOnTable || lineBasedOnId !== basedOnRequestId) {
      return false;
    }
    const pt = String(line.productTableName ?? '').trim();
    if (pt === ACTIVITY_DEFINITION_TABLE) {
      return String(line.servicePayload?.activityDefinitionId ?? '').trim() === targetId;
    }
    if (pt === DEVICE_DEFINITION_TABLE) {
      return String(line.devicePayload?.deviceDefinitionId ?? '').trim() === targetId;
    }
    return false;
  });
}

function bindItemAlreadyExists(
  item: DoctorStationAdviceDraftBindItemDto,
  basedOnRequestId: string | undefined,
  existingRows: AdviceRowLike[],
  lines: DoctorStationAdviceDraftLineRequest[],
): boolean {
  return (
    bindItemAlreadyExistsInDraftLines(item, basedOnRequestId, lines, existingRows) ||
    bindItemAlreadyExistsInAdviceList(item, basedOnRequestId, existingRows, lines)
  );
}

/** 过滤掉已有同锚点派生行或无效 active 的绑定候选 */
function filterResolvableDialogRowCandidates(
  dialogRowCandidates: AdviceDraftBindDialogRow[],
  lines: DoctorStationAdviceDraftLineRequest[],
  existingAdviceRows: AdviceRowLike[],
): AdviceDraftBindDialogRow[] {
  const immediateMap = buildImmediateTriggerRequestIdMap(dialogRowCandidates, lines);
  return dialogRowCandidates.filter((dialogRow) => {
    const basedOnRequestId = resolveTriggerRequestIdForBindItem(dialogRow.bindItem, immediateMap);
    return !bindItemAlreadyExists(dialogRow.bindItem, basedOnRequestId, existingAdviceRows, lines);
  });
}

/** 按来源缓存拆分：已缓存来源自动带出，未缓存来源进入弹窗 */
function splitDialogRowsBySourceSkipCache(
  dialogRows: AdviceDraftBindDialogRow[],
  userId: string,
): {
  autoRows: AdviceDraftBindDialogRow[];
  promptRows: AdviceDraftBindDialogRow[];
  promptSourceGroups: AdviceDraftBindDialogSourceGroup[];
} {
  const autoRows: AdviceDraftBindDialogRow[] = [];
  const promptRows: AdviceDraftBindDialogRow[] = [];
  const groupMetaByKey = new Map<string, { sourceKindLabel: string; sourceLabel: string }>();

  for (const row of dialogRows) {
    const sourceCacheKey = String(row.sourceCacheKey ?? '').trim();
    if (sourceCacheKey && isAdviceBindSourceSkipped(sourceCacheKey, userId)) {
      autoRows.push(row);
      continue;
    }
    promptRows.push(row);
    if (sourceCacheKey && !groupMetaByKey.has(sourceCacheKey)) {
      groupMetaByKey.set(sourceCacheKey, {
        sourceLabel: row.sourceItemName,
        sourceKindLabel: row.sourceKindLabel,
      });
    }
  }

  const promptSourceGroups: AdviceDraftBindDialogSourceGroup[] = [...groupMetaByKey.entries()].map(
    ([sourceCacheKey, meta]) => ({
      sourceCacheKey,
      sourceLabel: meta.sourceLabel,
      sourceKindLabel: meta.sourceKindLabel,
    }),
  );

  return { autoRows, promptRows, promptSourceGroups };
}

/** 从已有行快照诊疗 requestId，供 CREATE 后 diff */
function snapshotServiceRequestIds(rows: AdviceRowLike[]): Set<string> {
  const ids = new Set<string>();
  for (const row of rows) {
    if (!isServiceAdviceRow(row)) {
      continue;
    }
    const rid = preserveApiIdForRequest(row.requestId);
    if (rid) {
      ids.add(rid);
    }
  }
  return ids;
}

/** 收集批次中药品 CREATE 行下标（与 newMedicationRequestIds 顺序对齐） */
function collectMedicationCreateLineIndexes(
  lines: DoctorStationAdviceDraftLineRequest[],
): number[] {
  const indexes: number[] = [];
  lines.forEach((line, index) => {
    if (line.lineOp !== 'CREATE') {
      return;
    }
    const pt = String(line.productTableName ?? '').trim();
    if (pt === MEDICATION_DEFINITION_TABLE) {
      indexes.push(index);
    }
  });
  return indexes;
}

/** 收集批次中诊疗 CREATE 行（activityDefinitionId + 行下标） */
function collectServiceCreateLineEntries(
  lines: DoctorStationAdviceDraftLineRequest[],
): Array<{ activityDefinitionId: string; index: number }> {
  const entries: Array<{ activityDefinitionId: string; index: number }> = [];
  lines.forEach((line, index) => {
    if (line.lineOp !== 'CREATE') {
      return;
    }
    const pt = String(line.productTableName ?? '').trim();
    if (pt !== ACTIVITY_DEFINITION_TABLE) {
      return;
    }
    const activityDefinitionId = String(line.servicePayload?.activityDefinitionId ?? '').trim();
    if (activityDefinitionId) {
      entries.push({ index, activityDefinitionId });
    }
  });
  return entries;
}

/** 解析触发行 requestId：UPDATE 直接用 triggerRequestId；CREATE 从首轮暂存结果映射 */
function resolveTriggerRequestIdForBindItem(
  item: DoctorStationAdviceDraftBindItemDto,
  triggerRequestIdByLineIndex: Map<number, string>,
): string | undefined {
  const fromTrigger = preserveApiIdForRequest(item.triggerRequestId);
  if (fromTrigger) {
    return fromTrigger;
  }
  const fromBasedOn = preserveApiIdForRequest(item.basedOnRequestId);
  if (fromBasedOn) {
    return fromBasedOn;
  }
  const lineIndex = item.triggerLineIndex;
  if (lineIndex === undefined || lineIndex === null) {
    return undefined;
  }
  return triggerRequestIdByLineIndex.get(lineIndex);
}

/** 绑定项是否依赖 CREATE 触发行尚未落库的 requestId */
function bindItemNeedsDeferredSave(
  item: DoctorStationAdviceDraftBindItemDto,
  lines: DoctorStationAdviceDraftLineRequest[],
): boolean {
  const lineIndex = item.triggerLineIndex;
  if (lineIndex === undefined || lineIndex === null) {
    return false;
  }
  const triggerLine = lines[lineIndex];
  if (!triggerLine || triggerLine.lineOp !== 'CREATE') {
    return false;
  }
  return (
    !preserveApiIdForRequest(item.triggerRequestId) &&
    !preserveApiIdForRequest(triggerLine.requestId)
  );
}

/** 解析成组绑定耗材 packageId：与主药 groupId/packageId 同源；非成组返回 undefined */
function resolveBindPackageId(
  item: DoctorStationAdviceDraftBindItemDto,
  lines: DoctorStationAdviceDraftLineRequest[],
  existingRows: AdviceRowLike[],
): string | undefined {
  const usageGroup = resolveUsageGroupBindContext(item, lines);
  if (usageGroup?.groupId) {
    return usageGroup.groupId;
  }
  const basedOnId =
    preserveApiIdForRequest(item.basedOnRequestId) ??
    preserveApiIdForRequest(item.triggerRequestId);
  if (basedOnId) {
    for (const row of existingRows) {
      if (String(row.requestId ?? '').trim() !== basedOnId) {
        continue;
      }
      const fromPackage = String(row.packageId ?? '').trim();
      if (fromPackage) {
        return fromPackage;
      }
      const fromGroup = String(row.groupId ?? '').trim();
      if (fromGroup) {
        return fromGroup;
      }
    }
  }
  const lineGroupId = resolveMedicationLineGroupId(item.triggerLineIndex, lines);
  return lineGroupId || undefined;
}

/** 组装单条派生暂存行（诊疗 / 耗材）；sortNumber 继承锚定药品，与主医嘱聚邻展示 */
function buildBindDerivedDraftLine(
  item: DoctorStationAdviceDraftBindItemDto,
  basedOnRequestId: string,
  quantity: number,
  sortNumber?: number,
  packageId?: string,
): DoctorStationAdviceDraftLineRequest | undefined {
  const targetTable = String(item.targetProductTableName ?? '').trim();
  const targetId = String(item.targetItemId ?? '').trim();
  const basedOnTable = String(item.basedOnTable ?? '').trim();
  const unitCode = String(item.unitCode ?? '').trim() || 'U';
  const safeQty = Number.isFinite(quantity) && quantity > 0 ? quantity : 1;
  const sortNumberField = sortNumber !== undefined ? { sortNumber } : {};
  const packageIdField = packageId ? { packageId } : {};

  if (targetTable === ACTIVITY_DEFINITION_TABLE) {
    return {
      lineOp: 'CREATE',
      productTableName: ACTIVITY_DEFINITION_TABLE,
      basedOnTable: basedOnTable || MEDICATION_REQUEST_TABLE,
      basedOnId: basedOnRequestId,
      ...sortNumberField,
      servicePayload: {
        activityDefinitionId: targetId,
        quantity: safeQty,
        unitCode,
        executionCount: 1,
      },
    };
  }

  if (targetTable === DEVICE_DEFINITION_TABLE) {
    return {
      lineOp: 'CREATE',
      productTableName: DEVICE_DEFINITION_TABLE,
      basedOnTable: basedOnTable || MEDICATION_REQUEST_TABLE,
      basedOnId: basedOnRequestId,
      ...sortNumberField,
      ...packageIdField,
      devicePayload: {
        deviceDefinitionId: targetId,
        quantity: safeQty,
        unitCode,
      },
    };
  }

  return undefined;
}

/** 从触发行读取 sortNumber，供派生诊疗/耗材与锚定药品聚邻 */
function resolveBindTriggerLineSortNumber(
  item: DoctorStationAdviceDraftBindItemDto,
  lines: DoctorStationAdviceDraftLineRequest[],
): number | undefined {
  const lineIndex = item.triggerLineIndex;
  if (lineIndex === undefined || lineIndex === null) {
    return undefined;
  }
  const triggerLine = lines[lineIndex];
  if (!triggerLine) {
    return undefined;
  }
  const raw = triggerLine.sortNumber;
  if (raw === null || raw === undefined) {
    return undefined;
  }
  const n = typeof raw === 'number' ? raw : Number(raw);
  return Number.isFinite(n) && n > 0 ? Math.trunc(n) : undefined;
}

/** 将弹窗确认后的绑定行转为 IF-DOR-14 CREATE 行 */
function buildBindDerivedDraftLinesFromDialogRows(
  dialogRows: AdviceDraftBindDialogRow[],
  lines: DoctorStationAdviceDraftLineRequest[],
  triggerRequestIdByLineIndex: Map<number, string>,
  existingRows: AdviceRowLike[] = [],
): DoctorStationAdviceDraftLineRequest[] {
  const derived: DoctorStationAdviceDraftLineRequest[] = [];

  for (const dialogRow of dialogRows) {
    // 合并行按组展开；未合并则仅一条
    for (const item of resolveDialogRowBindItemsForPersist(dialogRow)) {
      const basedOnRequestId = resolveTriggerRequestIdForBindItem(
        item,
        triggerRequestIdByLineIndex,
      );
      if (!basedOnRequestId) {
        continue;
      }
      if (bindItemAlreadyExists(item, basedOnRequestId, existingRows, lines)) {
        continue;
      }
      const sortNumber = resolveBindTriggerLineSortNumber(item, lines);
      const packageId = resolveBindPackageId(item, lines, existingRows);
      const line = buildBindDerivedDraftLine(
        item,
        basedOnRequestId,
        dialogRow.quantity,
        sortNumber,
        packageId,
      );
      if (line) {
        derived.push(line);
      }
    }
  }

  return derived;
}

/** 构建 UPDATE 触发行可直接使用的 requestId 映射 */
function buildImmediateTriggerRequestIdMap(
  dialogRows: AdviceDraftBindDialogRow[],
  lines: DoctorStationAdviceDraftLineRequest[],
): Map<number, string> {
  const map = new Map<number, string>();
  for (const dialogRow of dialogRows) {
    for (const item of resolveDialogRowBindItemsForPersist(dialogRow)) {
      const lineIndex = item.triggerLineIndex;
      if (lineIndex === undefined || lineIndex === null) {
        continue;
      }
      const fromTrigger = preserveApiIdForRequest(item.triggerRequestId);
      const fromBasedOn = preserveApiIdForRequest(item.basedOnRequestId);
      const fromLine = preserveApiIdForRequest(lines[lineIndex]?.requestId);
      const requestId = fromTrigger ?? fromBasedOn ?? fromLine;
      if (requestId) {
        map.set(lineIndex, requestId);
      }
    }
  }
  return map;
}

/** 首轮暂存后解析 CREATE 触发行 requestId */
async function buildTriggerRequestIdMapAfterPrimarySave(
  lines: DoctorStationAdviceDraftLineRequest[],
  newMedicationRequestIds: string[],
  serviceRequestIdsBeforeSave: Set<string>,
  encounterId: string,
  patientId: string,
): Promise<Map<number, string>> {
  const map = new Map<number, string>();

  const medCreateIndexes = collectMedicationCreateLineIndexes(lines);
  medCreateIndexes.forEach((lineIndex, i) => {
    const id = preserveApiIdForRequest(newMedicationRequestIds[i]);
    if (id) {
      map.set(lineIndex, id);
    }
  });

  const serviceCreates = collectServiceCreateLineEntries(lines);
  if (serviceCreates.length === 0) {
    return map;
  }

  const listRes = await httpListDoctorStationAdvice({ encounterId, patientId });
  const { content: listRows } = parsePagedRowsFromApiData<DoctorStationAdviceRowDto>(listRes);
  const newServiceRows = listRows.filter((row) => {
    if (!isServiceAdviceRow(row)) {
      return false;
    }
    const rid = preserveApiIdForRequest(row.requestId);
    return Boolean(rid && !serviceRequestIdsBeforeSave.has(rid));
  });

  const usedRequestIds = new Set<string>();
  for (const entry of serviceCreates) {
    const hit = newServiceRows.find((row) => {
      const defId = String(row.productDefinitionId ?? '').trim();
      const rid = preserveApiIdForRequest(row.requestId);
      return defId === entry.activityDefinitionId && rid && !usedRequestIds.has(rid);
    });
    const rid = preserveApiIdForRequest(hit?.requestId);
    if (rid) {
      map.set(entry.index, rid);
      usedRequestIds.add(rid);
    }
  }

  return map;
}

/** 调用 IF-DOR-23 解析绑定并过滤无效项 */
async function resolveAdviceDraftBindDialogRows(
  options: Pick<SaveAdviceDraftBatchWithBindOptions, 'encounterId' | 'lines' | 'patientId'>,
): Promise<AdviceDraftBindDialogRow[]> {
  const resolveRes = await httpResolveDoctorStationAdviceDraftBinds({
    encounterId: options.encounterId,
    patientId: options.patientId,
    lines: options.lines,
  });
  let bindItems = dedupeAdviceDraftBindItems(resolveRes.bindItems ?? [], options.lines);
  bindItems = bindItems.filter((item) => item.activeFlag !== false);
  return toAdviceDraftBindDialogRows(bindItems, options.lines);
}

/** 已确认的带出行落库（含延迟锚点解析） */
async function saveAdviceDraftBatchWithConfirmedBindRows(
  options: SaveAdviceDraftBatchWithBindOptions,
  confirmedDialogRows: AdviceDraftBindDialogRow[],
): Promise<SaveDoctorStationAdviceDraftBatchResponse> {
  const { encounterId, patientId, lines, existingAdviceRows = [], pricingSourceCode } = options;
  const batchBody = {
    encounterId,
    patientId,
    lines,
    ...(pricingSourceCode ? { pricingSourceCode } : {}),
  };

  if (confirmedDialogRows.length === 0) {
    return httpSaveDoctorStationAdviceDraftBatch(batchBody);
  }

  const immediateMap = buildImmediateTriggerRequestIdMap(confirmedDialogRows, lines);

  const needsDeferred = confirmedDialogRows.some((dialogRow) =>
    resolveDialogRowBindItemsForPersist(dialogRow).some((item) =>
      bindItemNeedsDeferredSave(item, lines),
    ),
  );

  if (!needsDeferred) {
    const derivedLines = buildBindDerivedDraftLinesFromDialogRows(
      confirmedDialogRows,
      lines,
      immediateMap,
      existingAdviceRows,
    );
    return httpSaveDoctorStationAdviceDraftBatch({
      encounterId,
      patientId,
      lines: [...lines, ...derivedLines],
      ...(pricingSourceCode ? { pricingSourceCode } : {}),
    });
  }

  const serviceIdsBeforeSave = snapshotServiceRequestIds(existingAdviceRows);
  const primaryRes = await httpSaveDoctorStationAdviceDraftBatch(batchBody);
  if (primaryRes.success === false) {
    return primaryRes;
  }

  const deferredTriggerRequestIdMap = await buildTriggerRequestIdMapAfterPrimarySave(
    lines,
    primaryRes.newMedicationRequestIds ?? [],
    serviceIdsBeforeSave,
    encounterId,
    patientId,
  );
  const triggerRequestIdMap = new Map<number, string>(immediateMap);
  for (const [lineIndex, requestId] of deferredTriggerRequestIdMap) {
    triggerRequestIdMap.set(lineIndex, requestId);
  }

  const derivedLines = buildBindDerivedDraftLinesFromDialogRows(
    confirmedDialogRows,
    lines,
    triggerRequestIdMap,
    existingAdviceRows,
  );
  if (derivedLines.length === 0) {
    return primaryRes;
  }

  const bindRes = await httpSaveDoctorStationAdviceDraftBatch({
    encounterId,
    patientId,
    lines: derivedLines,
    ...(pricingSourceCode ? { pricingSourceCode } : {}),
  });
  if (bindRes.success === false) {
    return bindRes;
  }

  return {
    ...primaryRes,
    message: bindRes.message ?? primaryRes.message,
  };
}

/** IF-DOR-23 + 来源缓存 / 弹窗确认：仅新建药品触发带出；纯 UPDATE 编辑保存不二次弹窗 */
export async function saveAdviceDraftBatchWithBindResolution(
  options: SaveAdviceDraftBatchWithBindOptions,
): Promise<SaveDoctorStationAdviceDraftBatchResponse> {
  const { encounterId, patientId, lines, existingAdviceRows = [], pricingSourceCode } = options;
  const batchBody = {
    encounterId,
    patientId,
    lines,
    ...(pricingSourceCode ? { pricingSourceCode } : {}),
  };

  if (!hasMedicationCreateLine(lines)) {
    return httpSaveDoctorStationAdviceDraftBatch(batchBody);
  }

  let dialogRowCandidates = await resolveAdviceDraftBindDialogRows({
    encounterId,
    patientId,
    lines,
  });
  dialogRowCandidates = dialogRowCandidates.filter((row) =>
    isBindItemTriggeredByMedicationCreate(row.bindItem, lines),
  );

  if (dialogRowCandidates.length === 0) {
    return httpSaveDoctorStationAdviceDraftBatch(batchBody);
  }

  const resolvableRows = filterResolvableDialogRowCandidates(
    dialogRowCandidates,
    lines,
    existingAdviceRows,
  );

  if (resolvableRows.length === 0) {
    return httpSaveDoctorStationAdviceDraftBatch(batchBody);
  }

  // 同用法跨组合并为弹窗一行，确认后仍按组落派生行
  const displayRows = mergeUsageDialogRowsAcrossGroups(resolvableRows, lines);

  const cacheUserId = resolveAdviceBindSkipCacheUserId();
  const { autoRows, promptRows, promptSourceGroups } = splitDialogRowsBySourceSkipCache(
    displayRows,
    cacheUserId,
  );

  let confirmedDialogRows = [...autoRows];

  if (promptRows.length > 0) {
    const dialogResult = await openAdviceDraftBindDialog(promptRows, promptSourceGroups);
    if (!dialogResult) {
      // 取消绑定确认：仍暂存主医嘱，不带出绑定项
      return httpSaveDoctorStationAdviceDraftBatch(batchBody);
    }
    if (dialogResult.skipSourceKeys.length > 0) {
      addAdviceBindSkipSources(dialogResult.skipSourceKeys, cacheUserId);
    }
    confirmedDialogRows = [...autoRows, ...dialogResult.rows];
  }

  return saveAdviceDraftBatchWithConfirmedBindRows(options, confirmedDialogRows);
}
