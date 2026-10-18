import type { AdviceRowItem } from './advice.types';

import type {
  DoctorStationAdviceDraftLineRequest,
  DoctorStationAdviceRowDto,
  SaveDoctorStationAdviceDraftBatchResponse,
} from '#/api/doctor-station/advice.types';

import { saveDoctorStationAdviceDraftBatchWithSkinTestCrossLotConfirm } from './advice-skin-test-cross-lot-confirm';

/**
 * 门诊医生站 · 医嘱草稿暂存统一入口
 *
 * ## IF-DOR-23 用法/诊疗绑定带出策略（产品口径：签发时带出）
 *
 * | 场景 | 策略 | 函数 |
 * |------|------|------|
 * | 编辑卡手动保存 | DIRECT_ONLY | saveAdviceDraftBatchWithManualUsageBind → 仅主医嘱 |
 * | 组套套用 | DIRECT_ONLY | saveAdviceDraftBatchDirect（签发再跳过绑定，见 BR-ORD-038） |
 * | 历史医嘱复用 | DIRECT_ONLY | saveAdviceDraftBatchDirect |
 * | 删除草稿行 | DIRECT_ONLY | saveAdviceDraftBatchDirect |
 * | 工具栏「签发」 | SIGN_TIME_BIND | resolveAndPersistAdviceDraftBindsAtSign（见 bind.service） |
 *
 * **暂存不弹绑定**；**签发前**对本次待签草稿统一解析、确认（可改量/删行）、落派生后再扩 signTargets。
 * 去重仍由 bind.service 既有规则保证（合管耗材同设备同组一份、采血费跨设备/跨组一份且独立 SR、
 * 用法诊疗跨组一份、用法耗材按组一份、已有 based_on 不重复）。
 *
 * 新增暂存入口时：先对照上表选定策略，禁止直接调用 httpSaveDoctorStationAdviceDraftBatch。
 */

/** 暂存策略：仅落库变更行，不解析 IF-DOR-23 绑定 */
export const ADVICE_DRAFT_SAVE_DIRECT_ONLY = 'DIRECT_ONLY' as const;

/**
 * @deprecated 历史名：曾表示「保存时解析绑定」。现产品改为签发时带出，本常量与 DIRECT_ONLY 等价。
 * 保留常量以免调用方 import 断裂。
 */
export const ADVICE_DRAFT_SAVE_MANUAL_USAGE_BIND = 'MANUAL_USAGE_BIND' as const;

export type AdviceDraftSavePolicy =
  | typeof ADVICE_DRAFT_SAVE_DIRECT_ONLY
  | typeof ADVICE_DRAFT_SAVE_MANUAL_USAGE_BIND;

export interface SaveAdviceDraftBatchOptions {
  encounterId: string;
  patientId: string;
  lines: DoctorStationAdviceDraftLineRequest[];
  /** 签发前带出时由 bind.service 使用；暂存入口可忽略 */
  existingAdviceRows?: AdviceRowItem[] | DoctorStationAdviceRowDto[];
  pricingSourceCode?: string;
}

/** 直接暂存：不解析用法/诊疗绑定（删除、组套、历史医嘱、编辑保存等） */
export async function saveAdviceDraftBatchDirect(
  options: SaveAdviceDraftBatchOptions,
): Promise<SaveDoctorStationAdviceDraftBatchResponse> {
  const { encounterId, patientId, lines, pricingSourceCode } = options;
  return saveDoctorStationAdviceDraftBatchWithSkinTestCrossLotConfirm({
    encounterId,
    patientId,
    lines,
    ...(pricingSourceCode ? { pricingSourceCode } : {}),
  });
}

/**
 * 编辑卡「保存」：只落主医嘱（IF-DOR-14），不解析 IF-DOR-23。
 * 函数名保留兼容；绑定改在签发前 `resolveAndPersistAdviceDraftBindsAtSign`。
 */
export async function saveAdviceDraftBatchWithManualUsageBind(
  options: SaveAdviceDraftBatchOptions,
): Promise<SaveDoctorStationAdviceDraftBatchResponse> {
  return saveAdviceDraftBatchDirect(options);
}
