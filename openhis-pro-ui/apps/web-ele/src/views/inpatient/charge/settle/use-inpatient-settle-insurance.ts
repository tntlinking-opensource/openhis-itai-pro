import type { InpatientSettleQueueItemDto } from '#/api/inpatient-settle';

import { ElMessage } from 'element-plus';

import { httpGetInsurancePersonInfo } from '#/chs/api/insurance-person';
import {
  DEFAULT_CARD_READER_HOST,
  DEFAULT_CARD_READER_PORT,
  DEFAULT_SFZ_DRIVER_TYPE,
  formatYbPluginError,
  invokeIdCardRead,
  invokeSocialCardRead,
  isYbPluginOnline,
  resolveSocialCard1101Credential,
  YB_PLUGIN_FUNCTION_ID,
  type SocialCard1101Credential,
} from '#/chs/utils/yb-plugin';
import {
  chooseOutpatientChargeInsuranceMedium,
  outpatientChargeReadCredentialFailHint,
  outpatientChargeReadCredentialHint,
  outpatientChargeReadCredentialLabel,
  type OutpatientChargeInsuranceMedium,
} from '#/views/outpatient/charge/charge/use-outpatient-charge-insurance';
import {
  isInsuranceContractCategory,
  isSelfPayContractCategory,
  isWorkInjuryContractCategory,
} from '#/views/outpatient/charge/register/services/charge-type-category';
import { readYbEcVoucherSnapshot } from '#/views/outpatient/charge/register/services/read-yb-ec-voucher';

import { ENCOUNTER_STATUS_ON_HOLD, ENCOUNTER_STATUS_PENDING } from './inpatient-settle.constants';

export type InpatientSettleInsuranceMedium = OutpatientChargeInsuranceMedium;

export interface InpatientSettleInsuranceContext {
  contractId: string;
  insuplcAdmdvs?: string;
  insutype?: string;
  /** 本次读卡介质 */
  medium: InpatientSettleInsuranceMedium;
  psnNo: string;
  ybCardInfo: string;
  ybMediumType: string;
}

export {
  chooseOutpatientChargeInsuranceMedium as chooseInpatientSettleInsuranceMedium,
  outpatientChargeReadCredentialFailHint as inpatientSettleReadCredentialFailHint,
  outpatientChargeReadCredentialHint as inpatientSettleReadCredentialHint,
  outpatientChargeReadCredentialLabel as inpatientSettleReadCredentialLabel,
};

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

/** 读身份证并组 1101 凭证（对齐门诊收费）。 */
async function resolveIdCardCredential(): Promise<null | SocialCard1101Credential> {
  const card = await invokeIdCardRead({
    FunctionId: YB_PLUGIN_FUNCTION_ID.ID_CARD,
    IP: DEFAULT_CARD_READER_HOST,
    PORT: DEFAULT_CARD_READER_PORT,
    TIMEOUT: 60,
    LOG_PATH: 'C:/neu_log/',
    SFZ_DRIVER_TYPE: DEFAULT_SFZ_DRIVER_TYPE,
  });
  const certno = (card.idNumber ?? '').trim().toUpperCase();
  if (!certno) {
    ElMessage.warning('身份证读卡未返回有效证件号');
    return null;
  }
  const cardCipher = (card.signBuff ?? '').trim();
  if (!cardCipher) {
    ElMessage.warning(
      '读身份证未返回卡密（SignBuff），市医保结算可能失败，建议改刷社保卡或扫医保码',
    );
  }
  return {
    mdtrtCertType: '02',
    mdtrtCertNo: cardCipher || certno,
    certno,
    psnCertType: '01',
  };
}

/** 读社保卡并组 1101 凭证。 */
async function resolveSocialCardCredential(): Promise<{
  credential: SocialCard1101Credential;
  psnName?: string;
} | null> {
  const card = await invokeSocialCardRead({
    FunctionId: YB_PLUGIN_FUNCTION_ID.SOCIAL_CARD,
    IP: DEFAULT_CARD_READER_HOST,
    PORT: DEFAULT_CARD_READER_PORT,
    TIMEOUT: 60,
    LOG_PATH: 'C:/neu_log/',
    SFZ_DRIVER_TYPE: DEFAULT_SFZ_DRIVER_TYPE,
  });
  const credential = resolveSocialCard1101Credential(card);
  if (credential.mdtrtCertType === '03' && !(credential.cardSn ?? '').trim()) {
    ElMessage.error('社保卡读卡缺少卡识别码，请重新读卡');
    return null;
  }
  return {
    credential,
    psnName: (card.psnName ?? '').trim() || undefined,
  };
}

/** 扫医保码并组 1101 凭证。 */
async function resolveEcVoucherCredential(): Promise<{
  credential: SocialCard1101Credential;
  insuplcAdmdvs?: string;
  psnName?: string;
} | null> {
  const snapshot = await readYbEcVoucherSnapshot({ officeName: '住院结算' });
  const credential = snapshot?.insuranceCredential;
  if (!credential?.mdtrtCertNo) {
    return null;
  }
  const psnName = (snapshot.patientName ?? '').trim();
  if (credential.mdtrtCertType === '01' && (!(credential.certno ?? '').trim() || !psnName)) {
    ElMessage.warning('医保码未返回身份证号或姓名，无法调用医保');
    return null;
  }
  return {
    credential,
    insuplcAdmdvs: (snapshot.insuplcAdmdvs ?? '').trim() || undefined,
    psnName: psnName || undefined,
  };
}

/**
 * 按选定介质读卡/扫码并 1101，返回预结算所需医保上下文；插件离线返回 null。
 */
export async function resolveInpatientSettleInsuranceContext(
  contractId: string,
  medium: InpatientSettleInsuranceMedium,
): Promise<InpatientSettleInsuranceContext | null> {
  if (!(await isYbPluginOnline())) {
    return null;
  }
  try {
    let credential: SocialCard1101Credential;
    let psnName: string | undefined;
    let issuingAdmdvs: string | undefined;
    if (medium === 'yb-ec') {
      const ec = await resolveEcVoucherCredential();
      if (!ec) {
        return null;
      }
      credential = ec.credential;
      psnName = ec.psnName;
      issuingAdmdvs = ec.insuplcAdmdvs;
    } else if (medium === 'id') {
      const idCard = await resolveIdCardCredential();
      if (!idCard) {
        return null;
      }
      credential = idCard;
    } else {
      const social = await resolveSocialCardCredential();
      if (!social) {
        return null;
      }
      credential = social.credential;
      psnName = social.psnName;
    }

    const person = await httpGetInsurancePersonInfo({
      cardSn: credential.cardSn,
      certno: credential.certno,
      contractId,
      mdtrtCertType: credential.mdtrtCertType,
      mdtrtCertNo: credential.mdtrtCertNo,
      psnCertType: credential.psnCertType,
      psnName,
    });
    const psnNo = String(person.psnNo ?? '').trim();
    if (!psnNo) {
      ElMessage.warning('读卡未返回人员编号，请重试或检查费用性质');
      return null;
    }
    return {
      contractId,
      insuplcAdmdvs: person.insuplcAdmdvs || issuingAdmdvs,
      insutype: person.insutype,
      medium,
      psnNo,
      ybCardInfo: credential.mdtrtCertNo,
      ybMediumType: credential.mdtrtCertType,
    };
  } catch (error) {
    const message =
      error instanceof Error ? error.message : formatYbPluginError(error) || '医保读卡失败';
    ElMessage.error(message);
    return null;
  }
}

/** 医保读卡校验（工具栏「医保校验」）：先选介质再读卡。 */
export async function verifyInpatientInsuranceCard(contractId: string): Promise<void> {
  try {
    const medium = await chooseOutpatientChargeInsuranceMedium();
    if (!medium) {
      return;
    }
    const ctx = await resolveInpatientSettleInsuranceContext(contractId, medium);
    if (!ctx) {
      ElMessage.warning(
        (await isYbPluginOnline())
          ? outpatientChargeReadCredentialFailHint(medium)
          : '本机 OpenHIS.Win 未启动，无法进行医保读卡校验',
      );
      return;
    }
    ElMessage.success(`读卡成功，人员编号：${ctx.psnNo}`);
  } catch (error) {
    const message = error instanceof Error ? error.message : '医保读卡失败';
    ElMessage.error(message);
    throw error;
  }
}
