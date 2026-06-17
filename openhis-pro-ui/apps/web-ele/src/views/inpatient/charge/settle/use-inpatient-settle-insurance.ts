import type {
  ConfirmInpatientSettleResponse,
  InpatientSettleQueueItemDto,
} from '#/api/inpatient-settle';
import type { GetIpInsuranceSettleStatusResponse } from '#/api/insurance-inpatient-settlement';

import { ElMessage } from 'element-plus';

import { httpGetInpatientInsuranceSettleStatus } from '#/api/insurance-inpatient-settlement';
import { httpGetInsurancePersonInfo } from '#/chs/api/insurance-person';
import {
  DEFAULT_CARD_READER_HOST,
  DEFAULT_CARD_READER_PORT,
  invokeYbPlugin,
  isYbPluginOnline,
  YB_PLUGIN_FUNCTION_ID,
} from '#/chs/utils/yb-plugin';

import {
  ENCOUNTER_STATUS_PENDING,
  INPATIENT_SETTLEMENT_MODE,
  IP_INSURANCE_SETTLE_POLL_INTERVAL_MS,
  IP_INSURANCE_SETTLE_POLL_MAX_ATTEMPTS,
} from './inpatient-settle.constants';

const TERMINAL_COORDINATION_STATES = new Set([
  'COMPLETED',
  'FAILED',
  'UNKNOWN',
  'CANCELLED',
  'COMPENSATED',
]);

function sleep(ms: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

/** 正式结算响应是否为医保异步协同单路径。 */
export function isInpatientInsuranceAsyncConfirmResponse(
  response: ConfirmInpatientSettleResponse | null | undefined,
): boolean {
  if (!response) {
    return false;
  }
  return (
    response.settlementMode === INPATIENT_SETTLEMENT_MODE.INSURANCE_ASYNC &&
    Boolean(response.paymentId)
  );
}

export interface PollInpatientInsuranceSettleOptions {
  intervalMs?: number;
  maxAttempts?: number;
  onTick?: (status: GetIpInsuranceSettleStatusResponse, attempt: number) => void;
}

/**
 * 轮询 IP_SETTLE 协同单直至终态（2s 间隔，对齐门诊 §2.4）。
 *
 * @throws 协同单 FAILED / CANCELLED / 超时
 */
export async function pollInpatientInsuranceSettleStatus(
  paymentId: string,
  options?: PollInpatientInsuranceSettleOptions,
): Promise<GetIpInsuranceSettleStatusResponse> {
  const intervalMs = options?.intervalMs ?? IP_INSURANCE_SETTLE_POLL_INTERVAL_MS;
  const maxAttempts = options?.maxAttempts ?? IP_INSURANCE_SETTLE_POLL_MAX_ATTEMPTS;
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    const status = await httpGetInpatientInsuranceSettleStatus({ paymentId });
    options?.onTick?.(status, attempt);
    const state = String(status.state ?? '').trim();
    if (status.completed || state === 'COMPLETED' || status.paymentStatus === 'SUCCESS') {
      return status;
    }
    if (state === 'FAILED' || state === 'CANCELLED') {
      throw new Error(status.errorMsg?.trim() || '医保住院结算失败');
    }
    if (state === 'UNKNOWN') {
      throw new Error(status.errorMsg?.trim() || '结算结果待确认，请勿重复结算，请联系医保办处理');
    }
    if (TERMINAL_COORDINATION_STATES.has(state) && state !== 'COMPLETED') {
      throw new Error(status.errorMsg?.trim() || `医保协同单异常状态：${state}`);
    }
    if (attempt < maxAttempts) {
      await sleep(intervalMs);
    }
  }
  throw new Error('医保住院结算超时，请稍后在已结算页签核对或联系医保办');
}

const SELF_PAY_HINTS = ['自费', '现金', '个人'];

export interface InpatientSettleInsuranceContext {
  contractId: string;
  insuplcAdmdvs?: string;
  insutype?: string;
  psnNo: string;
  ybCardInfo: string;
  ybMediumType: string;
}

/** 登记账户是否为医保性质（用于决定是否读卡）。 */
export function isInpatientInsuranceRegistration(
  row: InpatientSettleQueueItemDto | null | undefined,
): boolean {
  if (!row) {
    return false;
  }
  if (row.contractId) {
    const display = String(row.contractDisplay ?? '').trim();
    if (display && SELF_PAY_HINTS.some((hint) => display.includes(hint))) {
      return false;
    }
    return true;
  }
  const display = String(row.contractDisplay ?? '').trim();
  if (!display) {
    return false;
  }
  if (SELF_PAY_HINTS.some((hint) => display.includes(hint))) {
    return false;
  }
  return display.includes('医保');
}

/**
 * 推导结算模式（与后端 §15.2 对齐；显式 requestSettleMode 由调用方传入）。
 */
export function resolveInpatientSettleMode(
  encounterStatus: string | undefined,
  selectedChargeItemIds: string[],
  allPendingChargeItemIds: string[],
  requestSettleMode?: string,
): string | undefined {
  const selected = new Set(selectedChargeItemIds);
  const allPending = new Set(allPendingChargeItemIds);
  const fullSelection =
    selected.size === allPending.size && [...selected].every((id) => allPending.has(id));

  if (requestSettleMode === 'MID_SETTLE') {
    return 'MID_SETTLE';
  }
  if (encounterStatus === ENCOUNTER_STATUS_PENDING) {
    return fullSelection ? 'DISCHARGE_FULL' : 'MID_SETTLE';
  }
  return fullSelection ? undefined : 'MID_SETTLE';
}

/** 读卡并 1101，返回预结算所需医保上下文；本机插件离线时返回 null（不抛错）。 */
export async function resolveInpatientSettleInsuranceContext(
  contractId: string,
): Promise<InpatientSettleInsuranceContext | null> {
  if (!(await isYbPluginOnline())) {
    return null;
  }
  const cardInfo = await invokeYbPlugin({
    FunctionId: YB_PLUGIN_FUNCTION_ID.SOCIAL_CARD,
    IP: DEFAULT_CARD_READER_HOST,
    PORT: DEFAULT_CARD_READER_PORT,
    TIMEOUT: 60,
    LOG_PATH: 'C:/neu_log/',
    SFZ_DRIVER_TYPE: 0,
  });
  const mdtrtCertNo = cardInfo.trim();
  if (!mdtrtCertNo) {
    throw new Error('读卡未返回有效凭证号');
  }
  const person = await httpGetInsurancePersonInfo({
    contractId,
    mdtrtCertType: '03',
    mdtrtCertNo,
  });
  return {
    contractId,
    insuplcAdmdvs: person.insuplcAdmdvs,
    insutype: person.insutype,
    psnNo: person.psnNo,
    ybCardInfo: mdtrtCertNo,
    ybMediumType: '03',
  };
}

/** 医保读卡校验（工具栏「医保校验」）。 */
export async function verifyInpatientInsuranceCard(contractId: string): Promise<void> {
  try {
    const ctx = await resolveInpatientSettleInsuranceContext(contractId);
    if (!ctx) {
      ElMessage.warning('本机 OpenHIS.Win 未启动，无法进行医保读卡校验');
      return;
    }
    ElMessage.success(`读卡成功，人员编号：${ctx.psnNo}`);
  } catch (error) {
    const message = error instanceof Error ? error.message : '医保读卡失败';
    ElMessage.error(message);
    throw error;
  }
}
