import type { AdviceRowItem } from './advice.types';

import type {
  InpatientDoctorStationAdviceDraftLineRequest,
  SaveInpatientDoctorStationAdviceDraftBatchResponse,
} from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.types';

import type {
  DoctorStationAdviceDraftLineRequest,
  DoctorStationAdviceRowDto,
} from '#/api/doctor-station/advice.types';

import { httpSaveInpatientDoctorStationAdviceDraftBatch } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';

import { inpatientAdviceDraftBindHttpAdapter } from '#/views/outpatient/doctor-station/advice/services/advice-draft-bind-http-adapter';
import { saveAdviceDraftBatchWithBindResolution } from '#/views/outpatient/doctor-station/advice/services/advice-draft-bind.service';

/** 住院医生站 · 医嘱草稿暂存策略（对齐门诊 advice-draft-save.service） */
export const INPATIENT_ADVICE_DRAFT_SAVE_DIRECT_ONLY = 'DIRECT_ONLY' as const;
export const INPATIENT_ADVICE_DRAFT_SAVE_MANUAL_USAGE_BIND = 'MANUAL_USAGE_BIND' as const;

export type InpatientAdviceDraftSavePolicy =
  | typeof INPATIENT_ADVICE_DRAFT_SAVE_DIRECT_ONLY
  | typeof INPATIENT_ADVICE_DRAFT_SAVE_MANUAL_USAGE_BIND;

export interface SaveInpatientAdviceDraftBatchOptions {
  encounterId: string;
  patientId: string;
  lines: InpatientDoctorStationAdviceDraftLineRequest[];
  existingAdviceRows?: AdviceRowItem[] | DoctorStationAdviceRowDto[];
}

/** 直接暂存：不解析 IF-IP-DOR-23 绑定 */
export async function saveInpatientAdviceDraftBatchDirect(
  options: SaveInpatientAdviceDraftBatchOptions,
): Promise<SaveInpatientDoctorStationAdviceDraftBatchResponse> {
  const { encounterId, patientId, lines } = options;
  return httpSaveInpatientDoctorStationAdviceDraftBatch({ encounterId, patientId, lines });
}

/** 手动编辑保存：新建药品/诊疗解析绑定并弹窗确认 */
export async function saveInpatientAdviceDraftBatchWithManualUsageBind(
  options: SaveInpatientAdviceDraftBatchOptions,
): Promise<SaveInpatientDoctorStationAdviceDraftBatchResponse> {
  const { encounterId, patientId, lines, existingAdviceRows } = options;
  return saveAdviceDraftBatchWithBindResolution({
    encounterId,
    patientId,
    lines: lines as DoctorStationAdviceDraftLineRequest[],
    existingAdviceRows,
    apiAdapter: inpatientAdviceDraftBindHttpAdapter,
  });
}
