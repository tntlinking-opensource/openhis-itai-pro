import type { AdviceRowItem } from './advice.types';

import type {
  InpatientDoctorStationAdviceDraftLineRequest,
  SaveInpatientDoctorStationAdviceDraftBatchResponse,
} from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.types';

import type { DoctorStationAdviceRowDto } from '#/api/doctor-station/advice.types';

import { httpSaveInpatientDoctorStationAdviceDraftBatch } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';

/** 住院医生站 · 医嘱草稿暂存策略（对齐门诊：暂存不绑；BR-IP-ORD-036 开立恒空） */
export const INPATIENT_ADVICE_DRAFT_SAVE_DIRECT_ONLY = 'DIRECT_ONLY' as const;
/**
 * @deprecated 历史名；住院开立不带绑定，与 DIRECT_ONLY 等价。
 */
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

/** 直接暂存：不解析 IF-IP-DOR-23 绑定（住院开立恒空） */
export async function saveInpatientAdviceDraftBatchDirect(
  options: SaveInpatientAdviceDraftBatchOptions,
): Promise<SaveInpatientDoctorStationAdviceDraftBatchResponse> {
  const { encounterId, patientId, lines } = options;
  return httpSaveInpatientDoctorStationAdviceDraftBatch({ encounterId, patientId, lines });
}

/**
 * 编辑卡「保存」：只落主医嘱。住院开立不带绑定（BR-IP-ORD-036）；函数名保留兼容。
 */
export async function saveInpatientAdviceDraftBatchWithManualUsageBind(
  options: SaveInpatientAdviceDraftBatchOptions,
): Promise<SaveInpatientDoctorStationAdviceDraftBatchResponse> {
  return saveInpatientAdviceDraftBatchDirect(options);
}
