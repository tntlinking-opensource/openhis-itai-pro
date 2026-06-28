import type {
  DoctorStationAdviceDraftLineRequest,
  DoctorStationAdviceRowDto,
  SaveDoctorStationServiceApplyFormRequest,
} from '#/api/doctor-station/advice.types';

import {
  httpListDoctorStationAdvice,
  httpSaveDoctorStationAdviceDraftBatch,
} from '#/api/doctor-station/advice.http';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  addAdviceBindSkipSources,
  resolveAdviceBindSkipCacheUserId,
} from '../advice/services/advice-bind-skip-cache.service';
import { openAdviceDraftBindDialog } from '../advice/services/advice-draft-bind-dialog.controller';
import {
  buildBindDerivedDraftLinesFromDialogRows,
  filterResolvableDialogRowCandidates,
  resolveAdviceDraftBindDialogRows,
  splitDialogRowsBySourceSkipCache,
} from '../advice/services/advice-draft-bind.service';
import { SERVICE_APPLY_ACTIVITY_TABLE } from './service-apply.constants';

export interface SaveServiceApplyFormWithBindOptions {
  encounterId: string;
  patientId: string;
  request: SaveDoctorStationServiceApplyFormRequest;
  stationScope: 'inpatient' | 'outpatient';
}

/** 申请单明细行 → IF-DOR-23 暂存变更行（诊疗 CREATE） */
export function buildServiceApplyBindTriggerLines(
  lines: SaveDoctorStationServiceApplyFormRequest['lines'],
): DoctorStationAdviceDraftLineRequest[] {
  return lines.map((line) => ({
    lineOp: 'CREATE',
    productTableName: SERVICE_APPLY_ACTIVITY_TABLE,
    servicePayload: {
      activityDefinitionId: line.activityDefinitionId,
      quantity: line.quantity,
      unitCode: line.unitCode,
      executionCount: 1,
    },
  }));
}

/** 加载当前就诊已有医嘱行，供绑定去重 */
async function loadExistingAdviceRows(
  encounterId: string,
  patientId: string,
): Promise<DoctorStationAdviceRowDto[]> {
  const listRes = await httpListDoctorStationAdvice({ encounterId, patientId, onlyMine: false });
  const { content } = parsePagedRowsFromApiData<DoctorStationAdviceRowDto>(listRes);
  return content;
}

/** IF-DOR-23 弹窗确认：返回用户确认的绑定行；取消则返回 null */
async function confirmServiceApplyBindDialogRows(
  encounterId: string,
  patientId: string,
  triggerLines: DoctorStationAdviceDraftLineRequest[],
  existingAdviceRows: DoctorStationAdviceRowDto[],
) {
  const dialogRowCandidates = await resolveAdviceDraftBindDialogRows({
    encounterId,
    patientId,
    lines: triggerLines,
  });
  if (dialogRowCandidates.length === 0) {
    return [] as typeof dialogRowCandidates;
  }

  const resolvableRows = filterResolvableDialogRowCandidates(
    dialogRowCandidates,
    triggerLines,
    existingAdviceRows,
  );
  if (resolvableRows.length === 0) {
    return [];
  }

  const cacheUserId = resolveAdviceBindSkipCacheUserId();
  const { autoRows, promptRows, promptSourceGroups } = splitDialogRowsBySourceSkipCache(
    resolvableRows,
    cacheUserId,
  );

  let confirmedDialogRows = [...autoRows];
  if (promptRows.length > 0) {
    const dialogResult = await openAdviceDraftBindDialog(promptRows, promptSourceGroups);
    if (!dialogResult) {
      return null;
    }
    if (dialogResult.skipSourceKeys.length > 0) {
      addAdviceBindSkipSources(dialogResult.skipSourceKeys, cacheUserId);
    }
    confirmedDialogRows = [...autoRows, ...dialogResult.rows];
  }

  return confirmedDialogRows;
}

/** 申请单保存结果 requestIds → 触发行锚点映射（顺序与 lines 一致） */
function buildServiceApplyTriggerRequestIdMap(requestIds: string[]): Map<number, string> {
  const map = new Map<number, string>();
  requestIds.forEach((requestId, index) => {
    const preserved = preserveApiIdForRequest(requestId);
    if (preserved) {
      map.set(index, preserved);
    }
  });
  return map;
}

/** 签发后按 IF-DOR-23 解析并带出绑定项（IF-DOR-14 落库）；暂存不调用 */
export async function saveServiceApplyBindItemsAfterApplyForm(
  options: Omit<SaveServiceApplyFormWithBindOptions, 'saveApplyForm'> & {
    applyResult: { prescriptionNo: string; requestIds: string[] };
  },
): Promise<void> {
  const { encounterId, patientId, request, stationScope, applyResult } = options;

  if (stationScope === 'inpatient') {
    return;
  }

  const triggerLines = buildServiceApplyBindTriggerLines(request.lines);
  const existingAdviceRows = await loadExistingAdviceRows(encounterId, patientId);
  const confirmedDialogRows = await confirmServiceApplyBindDialogRows(
    encounterId,
    patientId,
    triggerLines,
    existingAdviceRows,
  );

  // 用户取消绑定确认：仍完成签发，不带出绑定项
  if (confirmedDialogRows === null || confirmedDialogRows.length === 0) {
    return;
  }

  const triggerRequestIdMap = buildServiceApplyTriggerRequestIdMap(applyResult.requestIds);
  const derivedLines = buildBindDerivedDraftLinesFromDialogRows(
    confirmedDialogRows,
    triggerLines,
    triggerRequestIdMap,
    existingAdviceRows,
  );
  if (derivedLines.length === 0) {
    return;
  }

  const bindRes = await httpSaveDoctorStationAdviceDraftBatch({
    encounterId,
    patientId,
    lines: derivedLines,
  });
  if (bindRes.success === false) {
    throw new Error(String(bindRes.message ?? '').trim() || '绑定项暂存失败');
  }
}
