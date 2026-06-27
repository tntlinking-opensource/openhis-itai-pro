import type { PendingChargeItemRowDto } from '#/api/outpatient-charge/outpatient-charge.types';

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

const SELF_PAY_HINTS = ['自费', '现金', '个人'];

export interface OutpatientChargeInsuranceContext {
  contractId: string;
  insuplcAdmdvs?: string;
  insutype?: string;
  psnNo: string;
  ybCardInfo: string;
  ybMediumType: string;
}

/** 所选明细是否绑定多个记账账户。 */
export function hasMultipleChargeAccounts(rows: PendingChargeItemRowDto[]): boolean {
  const accountIds = new Set<string>();
  for (const row of rows) {
    accountIds.add(String(row.accountId ?? '__default__'));
  }
  return accountIds.size > 1;
}

/** 是否包含医保记账项。 */
export function selectionIncludesInsuranceItems(rows: PendingChargeItemRowDto[]): boolean {
  for (const row of rows) {
    if (row.contractId) {
      return true;
    }
    const nature = String(row.feeNatureDisplay ?? '').trim();
    if (!nature) {
      continue;
    }
    if (SELF_PAY_HINTS.some((hint) => nature.includes(hint))) {
      continue;
    }
    if (nature.includes('医保')) {
      return true;
    }
  }
  return false;
}

export function resolveInsuranceContractId(rows: PendingChargeItemRowDto[]): string | undefined {
  for (const row of rows) {
    const id = String(row.contractId ?? '').trim();
    if (id) {
      return id;
    }
  }
  return undefined;
}

/** 读卡并 1101，返回预结算所需医保上下文；纯自费返回 null。 */
export async function resolveOutpatientChargeInsuranceContext(
  rows: PendingChargeItemRowDto[],
): Promise<null | OutpatientChargeInsuranceContext> {
  if (!selectionIncludesInsuranceItems(rows)) {
    return null;
  }
  const contractId = resolveInsuranceContractId(rows);
  if (!contractId) {
    ElMessage.warning('所选费用项缺少医保合同，请先转医保或检查账户绑定');
    return null;
  }
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
