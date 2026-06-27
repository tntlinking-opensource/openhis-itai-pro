import type { InpatientSettleQueueItemDto } from '#/api/inpatient-settle';

import { ElMessage } from 'element-plus';

import { httpGetInsurancePersonInfo } from '#/chs/api/insurance-person';
import {
  DEFAULT_CARD_READER_HOST,
  DEFAULT_CARD_READER_PORT,
  invokeSocialCardRead,
  isYbPluginOnline,
  resolveSocialCard1101Credential,
  YB_PLUGIN_FUNCTION_ID,
} from '#/chs/utils/yb-plugin';

import { ENCOUNTER_STATUS_PENDING } from './inpatient-settle.constants';

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

/** 无待结算费用、就诊 PENDING：零元出院全结。 */
export function isZeroAmountDischargeFullSettle(
  encounterStatus: string | undefined,
  selectedChargeItemIds: string[],
  allPendingChargeItemIds: string[],
): boolean {
  return (
    selectedChargeItemIds.length === 0 &&
    allPendingChargeItemIds.length === 0 &&
    encounterStatus === ENCOUNTER_STATUS_PENDING
  );
}

/** 读卡并 1101，返回预结算所需医保上下文；本机插件离线时返回 null（不抛错）。 */
export async function resolveInpatientSettleInsuranceContext(
  contractId: string,
): Promise<InpatientSettleInsuranceContext | null> {
  if (!(await isYbPluginOnline())) {
    return null;
  }
  const card = await invokeSocialCardRead({
    FunctionId: YB_PLUGIN_FUNCTION_ID.SOCIAL_CARD,
    IP: DEFAULT_CARD_READER_HOST,
    PORT: DEFAULT_CARD_READER_PORT,
    TIMEOUT: 60,
    LOG_PATH: 'C:/neu_log/',
    SFZ_DRIVER_TYPE: 0,
  });
  const credential = resolveSocialCard1101Credential(card);
  const person = await httpGetInsurancePersonInfo({
    cardSn: credential.cardSn,
    certno: credential.certno,
    contractId,
    mdtrtCertType: credential.mdtrtCertType,
    mdtrtCertNo: credential.mdtrtCertNo,
    psnCertType: credential.psnCertType,
  });
  return {
    contractId,
    insuplcAdmdvs: person.insuplcAdmdvs,
    insutype: person.insutype,
    psnNo: person.psnNo,
    ybCardInfo: credential.mdtrtCertNo,
    ybMediumType: credential.mdtrtCertType,
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
