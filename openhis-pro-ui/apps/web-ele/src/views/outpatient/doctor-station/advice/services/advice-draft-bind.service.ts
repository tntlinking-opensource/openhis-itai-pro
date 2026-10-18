/**
 * IF-DOR-23 用法/诊疗绑定解析与落库。
 *
 * <p>产品口径（签发时带出）：暂存（IF-DOR-14）只落主医嘱；点签发前对本次待签草稿
 * 统一解析绑定、弹窗确认（可改量/删行，诊疗与耗材同权）、落派生后再扩 signTargets 签发。
 * 去重：合管耗材同批同设备同组一份；采血费同批跨设备/跨组只一份且独立 ServiceRequest（不挂 based_on）；
 * 用法绑定诊疗（如输液费）同用法同目标跨组只带一份；用法绑定耗材按药嘱组各带一份
 * （同组内同目标去重；弹窗数量=模板量×组数，落库时按组拆成多行）；已存在 based_on 派生不重复带。</p>
 */
import type { AdviceDraftBindDialogRow } from './advice-draft-bind.types';
import type { AdviceRowItem } from './advice.types';

import type {
  DoctorStationAdviceDraftBindItemDto,
  DoctorStationAdviceDraftLineRequest,
  DoctorStationAdviceResourceKeyDto,
  DoctorStationAdviceRowDto,
  SaveDoctorStationAdviceDraftBatchResponse,
} from '#/api/doctor-station/advice.types';

import { BUILT_IN_ACTIVITY_NAME_BLOOD_COLLECTION } from '#/constants/domain/built-in-activity-definition';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  type AdviceDraftBindHttpAdapter,
  outpatientAdviceDraftBindHttpAdapter,
} from './advice-draft-bind-http-adapter';

import {
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
import { resourceKeyFromRow, rowLooksDraftForSign } from './advice-row';
import { isOrderTemplateOriginRequestId } from './order-template-origin-cache.service';

export interface SaveAdviceDraftBatchWithBindOptions {
  encounterId: string;
  patientId: string;
  lines: DoctorStationAdviceDraftLineRequest[];
  /** 当前就诊已有医嘱行，用于去重与 CREATE 后锚点解析 */
  existingAdviceRows?: AdviceRowItem[] | DoctorStationAdviceRowDto[];
  pricingSourceCode?: string;
  /** 默认门诊；住院医生站 / 申请单传 inpatient 适配器 */
  apiAdapter?: AdviceDraftBindHttpAdapter;
  /** 主医嘱已落库（申请单签发等），仅解析并保存派生绑定行 */
  primaryLinesAlreadyPersisted?: boolean;
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

/** USAGE 用法级上下文 */
function resolveUsageMethodBindContext(
  item: DoctorStationAdviceDraftBindItemDto,
  lines: DoctorStationAdviceDraftLineRequest[],
): undefined | { methodCode: string } {
  if (String(item.bindType ?? '').trim() !== USAGE_BIND_TYPE) {
    return undefined;
  }
  const methodCode =
    resolveMedicationLineMethodCode(item.triggerLineIndex, lines) ||
    String(item.sourceItemId ?? '').trim();
  if (!methodCode) {
    return undefined;
  }
  return { methodCode };
}

/**
 * 用法绑定目标是否「跨组只带一份」。
 * 诊疗（输液费等 activity）跨组一份；耗材按药嘱组各一份。
 */
function isUsageBindCrossGroupOnceTarget(
  item: Pick<DoctorStationAdviceDraftBindItemDto, 'targetProductTableName'>,
): boolean {
  return String(item.targetProductTableName ?? '').trim() === ACTIVITY_DEFINITION_TABLE;
}

/**
 * 用法绑定触发行的成组键：有 groupId 用组号；否则 `line:下标`（未成组行各自一组）。
 */
function resolveUsageBindGroupKey(
  item: DoctorStationAdviceDraftBindItemDto,
  lines: DoctorStationAdviceDraftLineRequest[],
): string | undefined {
  const lineIndex = item.triggerLineIndex;
  if (lineIndex === undefined || lineIndex === null) {
    return undefined;
  }
  const groupId = resolveMedicationLineGroupId(lineIndex, lines);
  if (groupId) {
    return groupId;
  }
  return `line:${lineIndex}`;
}

/** 待签发池内：同用法药品 MR id（跨组；列表 + 当前变更行） */
function collectDraftMedicationRequestIdsForUsageMethod(
  methodCode: string,
  existingRows: AdviceRowLike[],
  lines: DoctorStationAdviceDraftLineRequest[],
): Set<string> {
  const ids = new Set<string>();
  for (const row of existingRows) {
    if (!rowEligibleForBindExistenceDedupe(row) || !isMedicationAdviceRow(row)) {
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

/**
 * 待签发池内：同用法且同药嘱组的药品 MR id（耗材按组判重）。
 * {@code groupKey} 为真实 groupId，或未成组时的 {@code line:下标}。
 */
function collectDraftMedicationRequestIdsForUsageMethodGroup(
  methodCode: string,
  groupKey: string,
  existingRows: AdviceRowLike[],
  lines: DoctorStationAdviceDraftLineRequest[],
): Set<string> {
  const ids = new Set<string>();
  const isLineKeyed = groupKey.startsWith('line:');
  if (!isLineKeyed) {
    for (const row of existingRows) {
      if (!rowEligibleForBindExistenceDedupe(row) || !isMedicationAdviceRow(row)) {
        continue;
      }
      if (String(row.methodCode ?? '').trim() !== methodCode) {
        continue;
      }
      const rowGroupId = String(row.groupId ?? row.packageId ?? '').trim();
      if (rowGroupId !== groupKey) {
        continue;
      }
      const rid = preserveApiIdForRequest(row.requestId);
      if (rid) {
        ids.add(rid);
      }
    }
  }
  lines.forEach((line, index) => {
    if (line.lineOp === 'DELETE') {
      return;
    }
    const pt = String(line.productTableName ?? '').trim();
    if (pt !== MEDICATION_DEFINITION_TABLE) {
      return;
    }
    const mc = String(line.medicationPayload?.methodCode ?? '').trim();
    if (mc !== methodCode) {
      return;
    }
    const gid = String(line.medicationPayload?.groupId ?? '').trim();
    const lineGroupKey = gid || `line:${index}`;
    if (lineGroupKey !== groupKey) {
      return;
    }
    const rid = preserveApiIdForRequest(line.requestId);
    if (rid) {
      ids.add(rid);
    }
  });
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

/** 合管采血费绑定项（名称精确匹配内置「采血费」；落库为独立 SR） */
export function isBloodCollectionFeeBindItem(
  item: Pick<
    DoctorStationAdviceDraftBindItemDto,
    'targetItemName' | 'targetProductTableName'
  >,
): boolean {
  const targetTable = String(item.targetProductTableName ?? '').trim();
  if (targetTable !== ACTIVITY_DEFINITION_TABLE) {
    return false;
  }
  return String(item.targetItemName ?? '').trim() === BUILT_IN_ACTIVITY_NAME_BLOOD_COLLECTION;
}

/** 医嘱列表行是否为采血费（按展示名精确匹配） */
export function isBloodCollectionFeeAdviceRow(row: AdviceRowLike): boolean {
  if (!isServiceAdviceRow(row)) {
    return false;
  }
  const name = String(
    ('content' in row ? row.content : undefined) ??
      ('contentSummary' in row ? row.contentSummary : undefined) ??
      ('adviceDisplayName' in row ? row.adviceDisplayName : undefined) ??
      '',
  ).trim();
  return name === BUILT_IN_ACTIVITY_NAME_BLOOD_COLLECTION;
}

/** 生成绑定去重键：采血费全批一份；合管耗材按设备+组；用法诊疗跨组一份；用法耗材按组；其余目标+锚点 */
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

  // 采血费：跨设备/跨组全局去重（不按合管组键）
  if (isBloodCollectionFeeBindItem(item)) {
    return `merge:blood-fee|${targetTable}|${targetId}`;
  }

  const mergeDeviceId = preserveApiIdForRequest(item.specimenTubeMergeDeviceId);
  if (mergeDeviceId) {
    const groupNo = String(item.specimenTubeMergeGroupNo ?? '').trim() || '1';
    return `merge:${mergeDeviceId}:${groupNo}|${targetTable}|${targetId}`;
  }

  // 用法绑定：诊疗跨组只保留一份；耗材按药嘱组各保留一份
  if (lines && String(item.bindType ?? '').trim() === USAGE_BIND_TYPE) {
    const methodCode =
      resolveMedicationLineMethodCode(item.triggerLineIndex, lines) ||
      String(item.sourceItemId ?? '').trim();
    if (methodCode) {
      if (isUsageBindCrossGroupOnceTarget(item)) {
        return `${targetTable}|${targetId}|usage:${methodCode}`;
      }
      const groupKey = resolveUsageBindGroupKey(item, lines) ?? basedOnId;
      return `${targetTable}|${targetId}|usage:${methodCode}|group:${groupKey}`;
    }
  }

  return `${targetTable}|${targetId}|${basedOnTable}|${basedOnId}`;
}

/** 变更行是否为新建药品（CREATE → USAGE 绑定带出） */
function isMedicationCreateDraftLine(line: DoctorStationAdviceDraftLineRequest): boolean {
  if (line.lineOp !== 'CREATE') {
    return false;
  }
  const productTableName = String(line.productTableName ?? '').trim();
  return productTableName === MEDICATION_DEFINITION_TABLE;
}

/** 变更行是否为新建诊疗（CREATE → ACTIVITY 绑定带出） */
function isServiceCreateDraftLine(line: DoctorStationAdviceDraftLineRequest): boolean {
  if (line.lineOp !== 'CREATE') {
    return false;
  }
  const productTableName = String(line.productTableName ?? '').trim();
  return productTableName === ACTIVITY_DEFINITION_TABLE;
}

/** 批次内是否含会触发绑定的新建行（药品用法 / 诊疗） */
function hasBindTriggerCreateLine(lines: readonly DoctorStationAdviceDraftLineRequest[]): boolean {
  return lines.some((line) => isMedicationCreateDraftLine(line) || isServiceCreateDraftLine(line));
}

/** 绑定项是否由新建药品/诊疗行触发（UPDATE 不参与二次带出） */
function isBindItemTriggeredByEligibleCreate(
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
  return isMedicationCreateDraftLine(triggerLine) || isServiceCreateDraftLine(triggerLine);
}

/** 服务端不去重，前端仅合并：① 用法诊疗跨组一份 / 用法耗材按组一份；② 目标+锚点完全相同的重复项 */
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

/** 去掉来源名上的「（N 组）」后缀，避免多次合并叠写 */
function stripUsageGroupCountSuffix(sourceItemName: string): string {
  return String(sourceItemName ?? '')
    .trim()
    .replace(/（\d+\s*组）$/u, '')
    .trim();
}

/** 统计变更行中同用法的药嘱组数（用于来源「N 组」展示；与落库份数无关） */
function countMedicationGroupsForMethodCode(
  methodCode: string,
  lines: DoctorStationAdviceDraftLineRequest[],
): number {
  const groups = new Set<string>();
  lines.forEach((line, index) => {
    if (line.lineOp === 'DELETE') {
      return;
    }
    if (String(line.productTableName ?? '').trim() !== MEDICATION_DEFINITION_TABLE) {
      return;
    }
    const mc = String(line.medicationPayload?.methodCode ?? '').trim();
    if (mc !== methodCode) {
      return;
    }
    const gid = String(line.medicationPayload?.groupId ?? '').trim();
    groups.add(gid || `line:${index}`);
  });
  return groups.size;
}

/**
 * 弹窗展示：USAGE 同目标同用法跨组合并为一行。
 * 诊疗（输液费）：确认后只落一份，数量=模板量。
 * 耗材：fanOut 各组各落一份；弹窗数量=模板量×组数（两组合计显示 2）。
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

    const crossGroupOnce = isUsageBindCrossGroupOnceTarget(row.bindItem);
    const perGroupQty = resolveBindQuantity(row.bindItem);
    // 展示键不含 groupId；诊疗与耗材均可合并展示，耗材落库时 fanOut 各组
    const mergeKey = `${targetTable}|${targetId}|usage:${methodCode}`;
    const groupCount = countMedicationGroupsForMethodCode(methodCode, lines);
    const baseSourceName = stripUsageGroupCountSuffix(row.sourceItemName);
    const sourceItemName =
      groupCount > 1 ? `${baseSourceName}（${groupCount} 组）` : baseSourceName;

    const existingIndex = usageMergeIndexByKey.get(mergeKey);
    if (existingIndex === undefined) {
      usageMergeIndexByKey.set(mergeKey, merged.length);
      merged.push({
        ...row,
        key: mergeKey,
        sourceItemName,
        quantity: perGroupQty,
        fanOutBindItems: [row.bindItem],
      });
      continue;
    }

    const existing = merged[existingIndex];
    if (!existing) {
      merged.push(row);
      continue;
    }
    if (crossGroupOnce) {
      // 诊疗：跨组只落首组一份，仅更新「N 组」展示
      merged[existingIndex] = {
        ...existing,
        sourceItemName,
      };
      continue;
    }
    // 耗材：追加各组触发项；弹窗数量按组累加（模板量×组数）
    const nextFanOut = [...(existing.fanOutBindItems ?? [existing.bindItem]), row.bindItem];
    merged[existingIndex] = {
      ...existing,
      sourceItemName,
      quantity: perGroupQty * nextFanOut.length,
      fanOutBindItems: nextFanOut,
    };
  }

  return merged;
}

/**
 * 弹窗行 → 待落库绑定项。
 * 诊疗跨组合并后只取首组；耗材 fanOut 各组各落一份。
 */
function resolveDialogRowBindItemsForPersist(
  dialogRow: AdviceDraftBindDialogRow,
): DoctorStationAdviceDraftBindItemDto[] {
  if (dialogRow.fanOutBindItems && dialogRow.fanOutBindItems.length > 0) {
    if (isUsageBindCrossGroupOnceTarget(dialogRow.bindItem)) {
      const first = dialogRow.fanOutBindItems[0];
      return first ? [first] : [dialogRow.bindItem];
    }
    return [...dialogRow.fanOutBindItems];
  }
  return [dialogRow.bindItem];
}

/**
 * 弹窗确认数量 → 落库单行数量。
 * 耗材按组 fanOut 时，弹窗数量为合计，需均分到各组（至少 1）。
 */
function resolvePersistQuantityPerBindItem(
  dialogRow: AdviceDraftBindDialogRow,
  fanOutCount: number,
): number {
  const total = Number.isFinite(dialogRow.quantity) && dialogRow.quantity > 0 ? dialogRow.quantity : 1;
  if (
    fanOutCount > 1 &&
    !isUsageBindCrossGroupOnceTarget(dialogRow.bindItem) &&
    String(dialogRow.bindItem.bindType ?? '').trim() === USAGE_BIND_TYPE
  ) {
    return Math.max(1, Math.round(total / fanOutCount));
  }
  return total;
}

/**
 * 待签发列表中是否已有同规则派生行（手动开立同名项不算重复；已签发不参与）。
 * 采血费按目标全局判重；USAGE 诊疗跨组一份、耗材按组一份；其余按 SR/MR 锚点。
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
  if (!targetId || !targetTable) {
    return false;
  }

  // 采血费独立 SR：待签发池内同目标只保留一份
  if (isBloodCollectionFeeBindItem(item)) {
    return existingRows.some((row) => {
      if (!rowEligibleForBindExistenceDedupe(row)) {
        return false;
      }
      return (
        listRowMatchesBindTarget(row, targetId, targetTable) || isBloodCollectionFeeAdviceRow(row)
      );
    });
  }

  if (!basedOnTable) {
    return false;
  }

  const usageMethod = resolveUsageMethodBindContext(item, lines);
  if (usageMethod && basedOnTable === MEDICATION_REQUEST_TABLE) {
    // 诊疗：同用法跨组任一已有派生则不再带；耗材：仅同组内已有则不再带
    const methodMrIds = isUsageBindCrossGroupOnceTarget(item)
      ? collectDraftMedicationRequestIdsForUsageMethod(
          usageMethod.methodCode,
          existingRows,
          lines,
        )
      : collectDraftMedicationRequestIdsForUsageMethodGroup(
          usageMethod.methodCode,
          resolveUsageBindGroupKey(item, lines) ?? `line:${item.triggerLineIndex ?? -1}`,
          existingRows,
          lines,
        );
    if (basedOnRequestId) {
      methodMrIds.add(basedOnRequestId);
    }
    if (methodMrIds.size === 0) {
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
      return Boolean(anchorId && methodMrIds.has(anchorId));
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

/** 当前暂存批次内是否已有同规则派生行（采血费按目标；USAGE 诊疗跨组 / 耗材按组；其余按锚点） */
function bindItemAlreadyExistsInDraftLines(
  item: DoctorStationAdviceDraftBindItemDto,
  basedOnRequestId: string | undefined,
  lines: DoctorStationAdviceDraftLineRequest[],
  existingRows: AdviceRowLike[] = [],
): boolean {
  const targetId = String(item.targetItemId ?? '').trim();
  const targetTable = String(item.targetProductTableName ?? '').trim();
  const basedOnTable = String(item.basedOnTable ?? '').trim();
  if (!targetId || !targetTable) {
    return false;
  }

  // 采血费：本批变更行内同 activity 已有 CREATE 则不再带
  if (isBloodCollectionFeeBindItem(item)) {
    return lines.some((line) => {
      if (line.lineOp === 'DELETE') {
        return false;
      }
      const pt = String(line.productTableName ?? '').trim();
      if (pt !== ACTIVITY_DEFINITION_TABLE) {
        return false;
      }
      return String(line.servicePayload?.activityDefinitionId ?? '').trim() === targetId;
    });
  }

  if (!basedOnTable) {
    return false;
  }

  const usageMethod = resolveUsageMethodBindContext(item, lines);
  if (usageMethod && basedOnTable === MEDICATION_REQUEST_TABLE) {
    const methodMrIds = isUsageBindCrossGroupOnceTarget(item)
      ? collectDraftMedicationRequestIdsForUsageMethod(
          usageMethod.methodCode,
          existingRows,
          lines,
        )
      : collectDraftMedicationRequestIdsForUsageMethodGroup(
          usageMethod.methodCode,
          resolveUsageBindGroupKey(item, lines) ?? `line:${item.triggerLineIndex ?? -1}`,
          existingRows,
          lines,
        );
    if (basedOnRequestId) {
      methodMrIds.add(basedOnRequestId);
    }
    if (methodMrIds.size === 0) {
      return false;
    }
    return lines.some((line) => {
      if (line.lineOp === 'DELETE') {
        return false;
      }
      const lineBasedOnTable = String(line.basedOnTable ?? '').trim();
      const lineBasedOnId = preserveApiIdForRequest(line.basedOnId);
      if (lineBasedOnTable !== basedOnTable || !lineBasedOnId || !methodMrIds.has(lineBasedOnId)) {
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
  // 采血费独立 SR：不挂 based_on，无需等待触发检验落库
  if (isBloodCollectionFeeBindItem(item) && !String(item.basedOnTable ?? '').trim()) {
    return false;
  }
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

/** 解析成组绑定耗材 packageId：与锚定主药 groupId/packageId 同源；非成组返回 undefined */
function resolveBindPackageId(
  item: DoctorStationAdviceDraftBindItemDto,
  lines: DoctorStationAdviceDraftLineRequest[],
  existingRows: AdviceRowLike[],
): string | undefined {
  // 优先用触发行成组号（诊疗跨组一份时锚定首组；耗材各组各自锚定）
  const lineGroupId = resolveMedicationLineGroupId(item.triggerLineIndex, lines);
  if (lineGroupId) {
    return lineGroupId;
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
  return undefined;
}

/** 触发行上的就诊诊断 ids 串；没有则空 */
function resolveServiceLineEncounterDiagnosisIds(
  line: DoctorStationAdviceDraftLineRequest | undefined,
): string | undefined {
  const text = String(line?.servicePayload?.encounterDiagnosisIds ?? '').trim();
  return text || undefined;
}

/**
 * 采血费诊断：优先触发检验行，没有则取本批第一条已绑诊断的诊疗行。
 * 同批采血费只落一份，诊断跟着带出它的那张检验。
 */
function resolveBloodFeeEncounterDiagnosisIds(
  item: DoctorStationAdviceDraftBindItemDto,
  lines: DoctorStationAdviceDraftLineRequest[],
): string | undefined {
  const lineIndex = item.triggerLineIndex;
  const triggerLine =
    lineIndex === undefined || lineIndex === null ? undefined : lines[lineIndex];
  const fromTrigger = resolveServiceLineEncounterDiagnosisIds(triggerLine);
  if (fromTrigger) {
    return fromTrigger;
  }
  for (const line of lines) {
    const ids = resolveServiceLineEncounterDiagnosisIds(line);
    if (ids) {
      return ids;
    }
  }
  return undefined;
}

/** 组装单条派生暂存行（诊疗 / 耗材）；采血费不写 based_on，但带入触发检验的诊断 */
function buildBindDerivedDraftLine(
  item: DoctorStationAdviceDraftBindItemDto,
  basedOnRequestId: string | undefined,
  quantity: number,
  sortNumber?: number,
  packageId?: string,
  encounterDiagnosisIds?: string,
): DoctorStationAdviceDraftLineRequest | undefined {
  const targetTable = String(item.targetProductTableName ?? '').trim();
  const targetId = String(item.targetItemId ?? '').trim();
  const basedOnTable = String(item.basedOnTable ?? '').trim();
  const unitCode = String(item.unitCode ?? '').trim() || 'U';
  const safeQty = Number.isFinite(quantity) && quantity > 0 ? quantity : 1;
  const sortNumberField = sortNumber !== undefined ? { sortNumber } : {};
  const packageIdField = packageId ? { packageId } : {};

  if (targetTable === ACTIVITY_DEFINITION_TABLE) {
    // 合管采血费：独立 ServiceRequest，不挂 based_on
    if (isBloodCollectionFeeBindItem(item) && !basedOnTable) {
      const diagnosisField = encounterDiagnosisIds ? { encounterDiagnosisIds } : {};
      return {
        lineOp: 'CREATE',
        productTableName: ACTIVITY_DEFINITION_TABLE,
        ...sortNumberField,
        servicePayload: {
          activityDefinitionId: targetId,
          quantity: safeQty,
          unitCode,
          executionCount: 1,
          ...diagnosisField,
        },
      };
    }
    if (!basedOnRequestId) {
      return undefined;
    }
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
    if (!basedOnRequestId) {
      return undefined;
    }
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
    // 诊疗跨组一份；耗材 fanOut 各组各一份（弹窗数量为合计时均分）
    const persistItems = resolveDialogRowBindItemsForPersist(dialogRow);
    const perItemQty = resolvePersistQuantityPerBindItem(dialogRow, persistItems.length);
    for (const item of persistItems) {
      const independentBloodFee =
        isBloodCollectionFeeBindItem(item) && !String(item.basedOnTable ?? '').trim();
      const basedOnRequestId = independentBloodFee
        ? undefined
        : resolveTriggerRequestIdForBindItem(item, triggerRequestIdByLineIndex);
      // 非独立采血费须有锚点 requestId；采血费可无 based_on 直接落库
      if (!independentBloodFee && !basedOnRequestId) {
        continue;
      }
      if (bindItemAlreadyExists(item, basedOnRequestId, existingRows, lines)) {
        continue;
      }
      const sortNumber = resolveBindTriggerLineSortNumber(item, lines);
      const packageId = resolveBindPackageId(item, lines, existingRows);
      const bloodFeeDiagnosis = independentBloodFee
        ? resolveBloodFeeEncounterDiagnosisIds(item, lines)
        : undefined;
      const line = buildBindDerivedDraftLine(
        item,
        basedOnRequestId,
        perItemQty,
        sortNumber,
        packageId,
        bloodFeeDiagnosis,
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
  apiAdapter: AdviceDraftBindHttpAdapter,
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

  const listRes = await apiAdapter.listAdvice({ encounterId, patientId });
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
  options: Pick<SaveAdviceDraftBatchWithBindOptions, 'encounterId' | 'lines' | 'patientId'> & {
    apiAdapter?: AdviceDraftBindHttpAdapter;
  },
): Promise<AdviceDraftBindDialogRow[]> {
  const apiAdapter = options.apiAdapter ?? outpatientAdviceDraftBindHttpAdapter;
  const resolveRes = await apiAdapter.resolveDraftBinds({
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
  const {
    encounterId,
    patientId,
    lines,
    existingAdviceRows = [],
    pricingSourceCode,
    apiAdapter = outpatientAdviceDraftBindHttpAdapter,
    primaryLinesAlreadyPersisted = false,
  } = options;
  const batchBody = {
    encounterId,
    patientId,
    lines,
    ...(pricingSourceCode ? { pricingSourceCode } : {}),
  };
  const okResponse = (): SaveDoctorStationAdviceDraftBatchResponse => ({
    success: true,
    message: 'OK',
  });

  if (confirmedDialogRows.length === 0) {
    if (primaryLinesAlreadyPersisted) {
      return okResponse();
    }
    return apiAdapter.saveDraftBatch(batchBody);
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
    if (derivedLines.length === 0) {
      return primaryLinesAlreadyPersisted ? okResponse() : apiAdapter.saveDraftBatch(batchBody);
    }
    if (primaryLinesAlreadyPersisted) {
      return apiAdapter.saveDraftBatch({
        encounterId,
        patientId,
        lines: derivedLines,
        ...(pricingSourceCode ? { pricingSourceCode } : {}),
      });
    }
    return apiAdapter.saveDraftBatch({
      encounterId,
      patientId,
      lines: [...lines, ...derivedLines],
      ...(pricingSourceCode ? { pricingSourceCode } : {}),
    });
  }

  if (primaryLinesAlreadyPersisted) {
    const derivedLines = buildBindDerivedDraftLinesFromDialogRows(
      confirmedDialogRows,
      lines,
      immediateMap,
      existingAdviceRows,
    );
    if (derivedLines.length === 0) {
      return okResponse();
    }
    return apiAdapter.saveDraftBatch({
      encounterId,
      patientId,
      lines: derivedLines,
      ...(pricingSourceCode ? { pricingSourceCode } : {}),
    });
  }

  const serviceIdsBeforeSave = snapshotServiceRequestIds(existingAdviceRows);
  const primaryRes = await apiAdapter.saveDraftBatch(batchBody);
  if (primaryRes.success === false) {
    return primaryRes;
  }

  const deferredTriggerRequestIdMap = await buildTriggerRequestIdMapAfterPrimarySave(
    lines,
    primaryRes.newMedicationRequestIds ?? [],
    serviceIdsBeforeSave,
    encounterId,
    patientId,
    apiAdapter,
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

  const bindRes = await apiAdapter.saveDraftBatch({
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

/** IF-DOR-23 + 来源缓存 / 弹窗确认：供签发前带出（主医嘱已落库） */
export async function saveAdviceDraftBatchWithBindResolution(
  options: SaveAdviceDraftBatchWithBindOptions,
): Promise<SaveDoctorStationAdviceDraftBatchResponse> {
  const {
    encounterId,
    patientId,
    lines,
    existingAdviceRows = [],
    pricingSourceCode,
    apiAdapter = outpatientAdviceDraftBindHttpAdapter,
    primaryLinesAlreadyPersisted = false,
  } = options;
  const batchBody = {
    encounterId,
    patientId,
    lines,
    ...(pricingSourceCode ? { pricingSourceCode } : {}),
  };
  const skipPrimarySave = (): SaveDoctorStationAdviceDraftBatchResponse => ({
    success: true,
    message: 'OK',
  });

  // 无药品/诊疗触发行时不解析绑定
  if (!hasBindTriggerCreateLine(lines)) {
    return primaryLinesAlreadyPersisted ? skipPrimarySave() : apiAdapter.saveDraftBatch(batchBody);
  }

  let dialogRowCandidates = await resolveAdviceDraftBindDialogRows({
    encounterId,
    patientId,
    lines,
    apiAdapter,
  });
  // 只保留由药品/诊疗触发行产生的绑定项（含签发前带 requestId 的 CREATE 伪行）
  dialogRowCandidates = dialogRowCandidates.filter((row) =>
    isBindItemTriggeredByEligibleCreate(row.bindItem, lines),
  );

  if (dialogRowCandidates.length === 0) {
    return primaryLinesAlreadyPersisted ? skipPrimarySave() : apiAdapter.saveDraftBatch(batchBody);
  }

  // 已存在 based_on 派生 / 用法诊疗跨组一份 / 用法耗材按组一份等同规则去重
  const resolvableRows = filterResolvableDialogRowCandidates(
    dialogRowCandidates,
    lines,
    existingAdviceRows,
  );

  if (resolvableRows.length === 0) {
    return primaryLinesAlreadyPersisted ? skipPrimarySave() : apiAdapter.saveDraftBatch(batchBody);
  }

  // 同用法合并为弹窗一行：诊疗一份；耗材数量按组累加并 fanOut 落库
  const displayRows = mergeUsageDialogRowsAcrossGroups(resolvableRows, lines);

  const dialogResult = await openAdviceDraftBindDialog(displayRows);
  if (!dialogResult) {
    // 取消绑定确认：不落派生，仍返回 success，由签发方继续签主医嘱（避免无法签发死锁）
    return primaryLinesAlreadyPersisted
      ? { success: true, message: 'BIND_DIALOG_CANCELLED' }
      : apiAdapter.saveDraftBatch(batchBody);
  }

  return saveAdviceDraftBatchWithConfirmedBindRows(options, dialogResult.rows);
}

/** 主医嘱已落库后解析并保存派生绑定（签发前统一带出） */
export async function saveAdviceDraftBindLinesAfterPrimaryPersisted(
  options: SaveAdviceDraftBatchWithBindOptions,
): Promise<SaveDoctorStationAdviceDraftBatchResponse> {
  return saveAdviceDraftBatchWithBindResolution({
    ...options,
    primaryLinesAlreadyPersisted: true,
  });
}

/** 仅保留耗材（adm_device_definition）绑定行（库存校验用） */
export function filterDeviceBindDialogRows(
  rows: AdviceDraftBindDialogRow[],
): AdviceDraftBindDialogRow[] {
  return rows.filter(
    (row) => String(row.bindItem.targetProductTableName ?? '').trim() === DEVICE_DEFINITION_TABLE,
  );
}

/** IF-DOR-23 绑定明细 → 弹窗可编辑行 */
export function mapDraftBindItemsToDialogRows(
  bindItems: DoctorStationAdviceDraftBindItemDto[],
  lines: DoctorStationAdviceDraftLineRequest[] = [],
): AdviceDraftBindDialogRow[] {
  return toAdviceDraftBindDialogRows(bindItems, lines);
}

/** 按暂存变更行解析绑定并转为弹窗行（不含弹窗确认） */
export async function resolveAdviceDraftBindDialogRowsFromLines(
  options: Pick<SaveAdviceDraftBatchWithBindOptions, 'encounterId' | 'lines' | 'patientId'> & {
    apiAdapter?: AdviceDraftBindHttpAdapter;
  },
): Promise<AdviceDraftBindDialogRow[]> {
  return resolveAdviceDraftBindDialogRows(options);
}

/** 主医嘱已落库：按已确认绑定行直接落库（跳过绑定确认弹窗） */
export async function persistAdviceDraftBindsWithConfirmedDialogRows(
  options: SaveAdviceDraftBatchWithBindOptions,
  confirmedDialogRows: AdviceDraftBindDialogRow[],
): Promise<SaveDoctorStationAdviceDraftBatchResponse> {
  return saveAdviceDraftBatchWithConfirmedBindRows(options, confirmedDialogRows);
}

/** 列表行是否为绑定派生行（based_on 锚定药品或诊疗） */
function isAdviceRowBindDerivedAnchor(row: AdviceRowItem): boolean {
  if (String(row.basedOnMedicationRequestId ?? '').trim()) {
    return true;
  }
  if (String(row.basedOnServiceRequestId ?? '').trim()) {
    return true;
  }
  return false;
}

/**
 * 待签发草稿主行 → IF-DOR-23 触发行（CREATE + 已有 requestId）。
 * 跳过已是 based_on 派生的行；药品须有 methodCode，诊疗须有 activityDefinitionId。
 */
export function buildAdviceBindTriggerLinesFromDraftRows(
  draftRows: readonly AdviceRowItem[],
): DoctorStationAdviceDraftLineRequest[] {
  const lines: DoctorStationAdviceDraftLineRequest[] = [];
  for (const row of draftRows) {
    // 派生行本身不再触发二次带出
    if (isAdviceRowBindDerivedAnchor(row)) {
      continue;
    }
    // 独立采血费不再作为合管/模板触发源
    if (isBloodCollectionFeeAdviceRow(row)) {
      continue;
    }
    const requestId = preserveApiIdForRequest(row.requestId);
    if (!requestId) {
      continue;
    }
    const definitionId =
      preserveApiIdForRequest(row.productDefinitionId) ??
      String(row.productDefinitionId ?? '').trim();
    if (!definitionId) {
      continue;
    }
    const pt = String(row.productTableName ?? '').trim();
    const rt = String(row.resourceType ?? '')
      .trim()
      .toUpperCase();
    // 药品：USAGE 绑定触发
    if (pt === MEDICATION_DEFINITION_TABLE || rt === 'MEDICATION') {
      const methodCode = String(row.methodCode ?? '').trim();
      if (!methodCode) {
        continue;
      }
      const groupId = String(row.groupId ?? row.packageId ?? '').trim();
      lines.push({
        lineOp: 'CREATE',
        productTableName: MEDICATION_DEFINITION_TABLE,
        requestId,
        medicationPayload: {
          medicationDefinitionId: definitionId,
          methodCode,
          ...(groupId ? { groupId, packageId: groupId } : {}),
        },
      });
      continue;
    }
    // 诊疗：ACTIVITY / 合管派生触发
    if (pt === ACTIVITY_DEFINITION_TABLE || rt === 'SERVICE') {
      const qtyRaw = Number(row.listQuantity ?? 1);
      const quantity = Number.isFinite(qtyRaw) && qtyRaw > 0 ? qtyRaw : 1;
      const unitCode = String(row.listUnitCode ?? '').trim() || 'U';
      const encounterDiagnosisIds = String(row.encounterDiagnosisIds ?? '').trim();
      lines.push({
        lineOp: 'CREATE',
        productTableName: ACTIVITY_DEFINITION_TABLE,
        requestId,
        servicePayload: {
          activityDefinitionId: definitionId,
          quantity,
          unitCode,
          ...(encounterDiagnosisIds ? { encounterDiagnosisIds } : {}),
        },
      });
    }
  }
  return lines;
}

/** 规范化签发目标 requestId */
function normalizeSignTargetRequestId(value: unknown): string {
  return preserveApiIdForRequest(value) ?? String(value ?? '').trim();
}

/**
 * 签发目标扩绑定派生草稿：主目标 + based_on 落在主目标上的待签发派生行
 * + 本批含合管检验时一并签发的独立采血费草稿（不挂 based_on）。
 */
export function expandAdviceSignTargetsWithBoundDrafts(
  primaryTargets: readonly DoctorStationAdviceResourceKeyDto[],
  allRows: readonly AdviceRowItem[],
): DoctorStationAdviceResourceKeyDto[] {
  const primaryIds = new Set(
    primaryTargets.map((t) => normalizeSignTargetRequestId(t.requestId)).filter(Boolean),
  );
  const dedupe = new Map<string, DoctorStationAdviceResourceKeyDto>();
  const addTarget = (target: DoctorStationAdviceResourceKeyDto) => {
    const requestId = normalizeSignTargetRequestId(target.requestId);
    const productTableName = String(target.productTableName ?? '').trim();
    if (!requestId || !productTableName) {
      return;
    }
    dedupe.set(`${productTableName}:${requestId}`, { requestId, productTableName });
  };
  for (const target of primaryTargets) {
    addTarget(target);
  }
  // 本批是否签发检验类诊疗（采血费随检验一并签，不依赖 based_on）
  let signingLabService = false;
  for (const row of allRows) {
    const rid = normalizeSignTargetRequestId(row.requestId);
    if (!rid || !primaryIds.has(rid)) {
      continue;
    }
    const cat = String(row.catalogCategoryCode ?? '')
      .trim()
      .toUpperCase();
    if (cat === '22' || cat === 'PROOF') {
      signingLabService = true;
      break;
    }
  }
  // 挂在本批主医嘱下的派生诊疗/耗材一并签发；独立采血费随检验签发扩入
  for (const row of allRows) {
    if (!rowLooksDraftForSign(row)) {
      continue;
    }
    const basedOnMr = normalizeSignTargetRequestId(row.basedOnMedicationRequestId);
    const basedOnSr = normalizeSignTargetRequestId(row.basedOnServiceRequestId);
    const anchored =
      (basedOnMr && primaryIds.has(basedOnMr)) || (basedOnSr && primaryIds.has(basedOnSr));
    const independentBloodFee = signingLabService && isBloodCollectionFeeAdviceRow(row);
    if (!anchored && !independentBloodFee) {
      continue;
    }
    const key = resourceKeyFromRow(row);
    if (key) {
      addTarget(key);
    }
  }
  return [...dedupe.values()];
}

/**
 * 签发前：对本次待签草稿解析绑定 → 弹窗确认 → 落派生（去重规则复用既有实现）。
 *
 * <p>组套套用产物（通用/申请单）不参与绑定解析，避免二次带出耗材/合管项（BR-ORD-038）。</p>
 *
 * @returns 无待带出或用户取消确认时仍返回 success（取消不落派生、继续签主医嘱）；落库失败则 success=false。
 * 取消时 {@code message=BIND_DIALOG_CANCELLED}，调用方可跳过为派生行刷新列表。
 */
export async function resolveAndPersistAdviceDraftBindsAtSign(params: {
  encounterId: string;
  patientId: string;
  draftRows: readonly AdviceRowItem[];
  existingAdviceRows?: AdviceRowItem[] | DoctorStationAdviceRowDto[];
  apiAdapter?: AdviceDraftBindHttpAdapter;
}): Promise<SaveDoctorStationAdviceDraftBatchResponse> {
  const bindLines = buildAdviceBindTriggerLinesFromDraftRows(params.draftRows).filter(
    (line) => !isOrderTemplateOriginRequestId(params.encounterId, line.requestId),
  );
  // 本批待签均来自组套：不调 IF-DOR-23，仅签组套内已开内容
  if (bindLines.length === 0) {
    return { success: true, message: 'OK' };
  }
  return saveAdviceDraftBindLinesAfterPrimaryPersisted({
    encounterId: params.encounterId,
    patientId: params.patientId,
    lines: bindLines,
    existingAdviceRows: params.existingAdviceRows ?? params.draftRows,
    apiAdapter: params.apiAdapter ?? outpatientAdviceDraftBindHttpAdapter,
  });
}
