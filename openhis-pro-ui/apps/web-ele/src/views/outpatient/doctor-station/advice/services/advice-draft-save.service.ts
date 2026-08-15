import type { AdviceRowItem } from './advice.types';

import type {
  DoctorStationAdviceDraftLineRequest,
  DoctorStationAdviceRowDto,
  SaveDoctorStationAdviceDraftBatchResponse,
} from '#/api/doctor-station/advice.types';

import { httpSaveDoctorStationAdviceDraftBatch } from '#/api/doctor-station/advice.http';

import { saveAdviceDraftBatchWithBindResolution } from './advice-draft-bind.service';

/**
 * 门诊医生站 · 医嘱草稿暂存统一入口
 *
 * ## IF-DOR-23 用法/诊疗绑定带出策略
 *
 * | 场景 | 策略 | 函数 |
 * |------|------|------|
 * | 编辑卡手动保存（新建药品/诊疗 CREATE） | MANUAL_USAGE_BIND | saveAdviceDraftBatchWithManualUsageBind |
 * | 编辑卡手动保存（纯 UPDATE / DELETE） | DIRECT_ONLY 同等 | 同上入口内自动跳过绑定 |
 * | 组套套用 | DIRECT_ONLY | saveAdviceDraftBatchDirect |
 * | 历史医嘱复用 | DIRECT_ONLY | saveAdviceDraftBatchDirect |
 * | 删除草稿行 | DIRECT_ONLY | saveAdviceDraftBatchDirect |
 *
 * **手动开立绑定带出** 与 **批量套用/编辑保存不带出** 是独立链路：
 * - 新建药品（CREATE）→ 解析 USAGE 用法绑定并弹窗确认；
 * - 新建诊疗（CREATE）→ 解析 ACTIVITY 诊疗绑定并弹窗确认；
 * - 纯编辑（UPDATE）、组套、历史医嘱复用均不二次带出。
 *
 * 新增暂存入口时：先对照上表选定策略，禁止直接调用 httpSaveDoctorStationAdviceDraftBatch。
 */

/** 暂存策略：仅落库变更行，不解析 IF-DOR-23 绑定 */
export const ADVICE_DRAFT_SAVE_DIRECT_ONLY = 'DIRECT_ONLY' as const;

/** 暂存策略：手动医嘱保存，解析用法/诊疗绑定并弹窗确认后带出诊疗/耗材 */
export const ADVICE_DRAFT_SAVE_MANUAL_USAGE_BIND = 'MANUAL_USAGE_BIND' as const;

export type AdviceDraftSavePolicy =
  | typeof ADVICE_DRAFT_SAVE_DIRECT_ONLY
  | typeof ADVICE_DRAFT_SAVE_MANUAL_USAGE_BIND;

export interface SaveAdviceDraftBatchOptions {
  encounterId: string;
  patientId: string;
  lines: DoctorStationAdviceDraftLineRequest[];
  /** MANUAL_USAGE_BIND 时传入，用于绑定去重与锚点解析 */
  existingAdviceRows?: AdviceRowItem[] | DoctorStationAdviceRowDto[];
  pricingSourceCode?: string;
}

/** 直接暂存：不解析用法/诊疗绑定（删除、组套、历史医嘱等） */
export async function saveAdviceDraftBatchDirect(
  options: SaveAdviceDraftBatchOptions,
): Promise<SaveDoctorStationAdviceDraftBatchResponse> {
  const { encounterId, patientId, lines, pricingSourceCode } = options;
  return httpSaveDoctorStationAdviceDraftBatch({
    encounterId,
    patientId,
    lines,
    ...(pricingSourceCode ? { pricingSourceCode } : {}),
  });
}

/** 手动编辑保存：新建药品→用法绑定、新建诊疗→诊疗绑定；纯 UPDATE 直接暂存 */
export async function saveAdviceDraftBatchWithManualUsageBind(
  options: SaveAdviceDraftBatchOptions,
): Promise<SaveDoctorStationAdviceDraftBatchResponse> {
  const { encounterId, patientId, lines, existingAdviceRows, pricingSourceCode } = options;
  return saveAdviceDraftBatchWithBindResolution({
    encounterId,
    patientId,
    lines,
    existingAdviceRows,
    pricingSourceCode,
  });
}
