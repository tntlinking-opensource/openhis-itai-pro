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

import { ENCOUNTER_STATUS_ON_HOLD, ENCOUNTER_STATUS_PENDING } from './inpatient-settle.constants';
import {
  isInsuranceContractCategory,
  isSelfPayContractCategory,
  isWorkInjuryContractCategory,
} from '#/views/outpatient/charge/register/services/charge-type-category';

export interface InpatientSettleInsuranceContext {
  contractId: string;
  insuplcAdmdvs?: string;
  insutype?: string;
  psnNo: string;
  ybCardInfo: string;
  ybMediumType: string;
}

/** 登记账户是否为医保性质（用于决定是否读卡；按 contractId 对应类别码判断）。 */
export function isInpatientInsuranceRegistration(
  row: InpatientSettleQueueItemDto | null | undefined,
  contractCategoryById?: Record<string, string>,
): boolean {
  if (!row?.contractId) {
    return false;
  }
  const category = String(contractCategoryById?.[String(row.contractId)] ?? '').trim();
  if (!category) {
    return true;
  }
  if (isSelfPayContractCategory(category)) {
    return false;
  }
  return isInsuranceContractCategory(category) || isWorkInjuryContractCategory(category);
}

function isFullPendingSelection(
  selectedChargeItemIds: string[],
  allPendingChargeItemIds: string[],
): boolean {
  const selected = new Set(selectedChargeItemIds);
  const allPending = new Set(allPendingChargeItemIds);
  return selected.size === allPending.size && [...selected].every((id) => allPending.has(id));
}

/**
 * 推导结算模式：本期仅出院全结（PENDING + 全选待结算）；无中途结算。
 */
export function resolveInpatientSettleMode(
  encounterStatus: string | undefined,
  selectedChargeItemIds: string[],
  allPendingChargeItemIds: string[],
  requestSettleMode?: string,
): string | undefined {
  if (requestSettleMode === 'MID_SETTLE') {
    return undefined;
  }
  const status = String(encounterStatus ?? '').trim();
  if (status === ENCOUNTER_STATUS_ON_HOLD) {
    return undefined;
  }
  if (status !== ENCOUNTER_STATUS_PENDING) {
    return undefined;
  }
  if (!isFullPendingSelection(selectedChargeItemIds, allPendingChargeItemIds)) {
    return undefined;
  }
  return 'DISCHARGE_FULL';
}

/** 待出院态禁止任何结算 */
export function isInpatientSettleBlockedByDischargeHold(
  encounterStatus: string | undefined,
): boolean {
  return String(encounterStatus ?? '').trim() === ENCOUNTER_STATUS_ON_HOLD;
}

/** 结算按钮禁用原因（与后端 EX-IP-STL-107/108/111/112 对齐） */
export function inpatientSettleBlockedMessage(
  encounterStatus: string | undefined,
  selectedChargeItemIds: string[],
  allPendingChargeItemIds: string[],
): string | undefined {
  const status = String(encounterStatus ?? '').trim();
  if (status === ENCOUNTER_STATUS_ON_HOLD) {
    return '请先办理护士出院后再结算';
  }
  if (status !== ENCOUNTER_STATUS_PENDING) {
    return '请先完成护士出院后再结算';
  }
  if (
    allPendingChargeItemIds.length > 0 &&
    !isFullPendingSelection(selectedChargeItemIds, allPendingChargeItemIds)
  ) {
    return '请全选待结算费用';
  }
  return undefined;
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
