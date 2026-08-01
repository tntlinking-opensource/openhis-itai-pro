/**
 * 事前提醒主路径门禁 composable：3101 → 对话框 → 3103。
 */
import type { Ref } from 'vue';

import { ElMessage } from 'element-plus';

import {
  analyzeIpGateApi,
  analyzeOpGateApi,
  gateHasViolations,
  type DetailAuditGateResult,
  type DetailAuditIpGateParams,
  type DetailAuditOpGateParams,
} from '#/chs/api/detail-audit-gate';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';

export interface DetailAuditRemindDialogExpose {
  openAndWait: (
    result: NonNullable<DetailAuditGateResult['analyzeResult']>,
    meta?: { contractBusNo?: string; contractId?: number | string },
  ) => Promise<boolean>;
}

/**
 * 门诊门禁：签到 → 3101 → 无违规直通；有违规弹窗后按用户选择返回。
 * 平台失败由 API 抛错，调用方须阻断主路径。
 */
export async function runOpDetailAuditGate(options: {
  params: DetailAuditOpGateParams;
  dialogRef: Ref<DetailAuditRemindDialogExpose | null | undefined>;
  loadingHint?: string;
}): Promise<{ continued: boolean; gate: DetailAuditGateResult }> {
  // 有契约则先签到，避免平台会话过期（服务端 sign_mac 回退为设计内能力，不打扰临床）
  const busNo = options.params.contractBusNo?.trim();
  if (busNo) {
    await ensureInsuranceSignIn(busNo, { warnOnServerFallback: false });
  }
  if (options.loadingHint) {
    ElMessage.info(options.loadingHint);
  }
  const gate = await analyzeOpGateApi(options.params);
  // 无违规：不打断主路径
  if (!gateHasViolations(gate)) {
    return { continued: true, gate };
  }
  const analyze = gate.analyzeResult;
  if (!analyze) {
    throw new Error('事前提醒结果为空');
  }
  const dialog = options.dialogRef.value;
  if (!dialog) {
    throw new Error('事前提醒对话框未就绪');
  }
  // 有违规：等用户 3103 处置（继续/返回修改）
  const continued = await dialog.openAndWait(analyze, {
    contractBusNo: gate.contractBusNo || options.params.contractBusNo,
    contractId: options.params.contractId,
  });
  return { continued, gate };
}

/**
 * 住院门禁：同构门诊（签到 → 3101 → 对话框）。
 */
export async function runIpDetailAuditGate(options: {
  params: DetailAuditIpGateParams;
  dialogRef: Ref<DetailAuditRemindDialogExpose | null | undefined>;
  loadingHint?: string;
}): Promise<{ continued: boolean; gate: DetailAuditGateResult }> {
  const busNo = options.params.contractBusNo?.trim();
  if (busNo) {
    await ensureInsuranceSignIn(busNo, { warnOnServerFallback: false });
  }
  if (options.loadingHint) {
    ElMessage.info(options.loadingHint);
  }
  const gate = await analyzeIpGateApi(options.params);
  if (!gateHasViolations(gate)) {
    return { continued: true, gate };
  }
  const analyze = gate.analyzeResult;
  if (!analyze) {
    throw new Error('事前提醒结果为空');
  }
  const dialog = options.dialogRef.value;
  if (!dialog) {
    throw new Error('事前提醒对话框未就绪');
  }
  const continued = await dialog.openAndWait(analyze, {
    contractBusNo: gate.contractBusNo || options.params.contractBusNo,
    contractId: options.params.contractId,
  });
  return { continued, gate };
}
