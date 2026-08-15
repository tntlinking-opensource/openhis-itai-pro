import type { PendingChargeItemRowDto } from '#/api/outpatient-charge/outpatient-charge.types';
import type { SocialCard1101Credential } from '#/chs/utils/yb-plugin';

import { h, ref } from 'vue';

import { ElMessage, ElMessageBox, ElRadioButton, ElRadioGroup } from 'element-plus';

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
} from '#/chs/utils/yb-plugin';
import {
  isInsuranceContractCategory,
  isPublicChargeContractCategory,
  isSelfPayContractCategory,
  isWorkInjuryContractCategory,
} from '#/views/outpatient/charge/register/services/charge-type-category';
import { readYbEcVoucherSnapshot } from '#/views/outpatient/charge/register/services/read-yb-ec-voucher';

/** 收费医保凭证：身份证(02) / 社保卡(03) / 医保码(01) */
export type OutpatientChargeInsuranceMedium = 'id' | 'yb-card' | 'yb-ec';

export interface OutpatientChargeInsuranceContext {
  /** 1101 个人账户余额（个账） */
  balc?: number | string;
  contractId: string;
  insuplcAdmdvs?: string;
  insutype?: string;
  /** 本次读卡介质 */
  medium: OutpatientChargeInsuranceMedium;
  psnNo: string;
  ybCardInfo: string;
  ybMediumType: string;
}

function resolvePersonBalc(person: {
  balc?: number | string;
  insuinfoList?: Array<{ balc?: number | string }>;
}): number | string | undefined {
  if (person.balc !== undefined && person.balc !== null && person.balc !== '') {
    return person.balc;
  }
  for (const row of person.insuinfoList ?? []) {
    if (row.balc !== undefined && row.balc !== null && row.balc !== '') {
      return row.balc;
    }
  }
  return undefined;
}

/** 所选明细是否绑定多个记账账户。 */
export function hasMultipleChargeAccounts(rows: PendingChargeItemRowDto[]): boolean {
  const accountIds = new Set<string>();
  for (const row of rows) {
    accountIds.add(String(row.accountId ?? '__default__'));
  }
  return accountIds.size > 1;
}

/**
 * 是否包含医保/工伤记账项。
 * <p>必须以账户绑定的费用性质为准（{@code contractCategoryCode} / 费用性质名），
 * 禁止用药品目录 {@code chargeCategoryCode}（否则会漏读卡触发 EX-OPC-306）。</p>
 */
export function selectionIncludesInsuranceItems(rows: PendingChargeItemRowDto[]): boolean {
  for (const row of rows) {
    const contractCat = String(row.contractCategoryCode ?? '').trim();
    if (contractCat) {
      if (isSelfPayContractCategory(contractCat) || isPublicChargeContractCategory(contractCat)) {
        continue;
      }
      if (isInsuranceContractCategory(contractCat) || isWorkInjuryContractCategory(contractCat)) {
        return true;
      }
    }
    const feeNature = String(row.feeNatureDisplay ?? '').trim();
    if (feeNature.includes('医保') || feeNature.includes('工伤')) {
      return true;
    }
  }
  return false;
}

/** 勾选行是否含工伤费用性质（收费走 Win，跳过省医保读卡/1101/3101）。 */
export function selectionIncludesWorkInjuryItems(rows: PendingChargeItemRowDto[]): boolean {
  for (const row of rows) {
    const contractCat = String(row.contractCategoryCode ?? '').trim();
    if (isWorkInjuryContractCategory(contractCat)) {
      return true;
    }
    const feeNature = String(row.feeNatureDisplay ?? '').trim();
    if (feeNature.includes('工伤')) {
      return true;
    }
  }
  return false;
}

export function resolveInsuranceContractId(rows: PendingChargeItemRowDto[]): string | undefined {
  for (const row of rows) {
    const contractCat = String(row.contractCategoryCode ?? '').trim();
    const feeNature = String(row.feeNatureDisplay ?? '').trim();
    const isInsurance =
      isInsuranceContractCategory(contractCat) ||
      isWorkInjuryContractCategory(contractCat) ||
      feeNature.includes('医保') ||
      feeNature.includes('工伤');
    if (!isInsurance) continue;
    const id = String(row.contractId ?? '').trim();
    if (id) return id;
  }
  for (const row of rows) {
    const id = String(row.contractId ?? '').trim();
    if (id) return id;
  }
  return undefined;
}

export type OutpatientChargeInsurancePhase = 'plugin' | 'read-card' | 'person-1101';

/** 进度条文案：读凭证步骤标题 */
export function outpatientChargeReadCredentialLabel(
  medium: OutpatientChargeInsuranceMedium,
): string {
  if (medium === 'yb-ec') {
    return '扫描医保电子凭证';
  }
  if (medium === 'id') {
    return '读取身份证';
  }
  return '读取社保卡';
}

/** 进度条文案：读凭证进行中提示 */
export function outpatientChargeReadCredentialHint(
  medium: OutpatientChargeInsuranceMedium,
): string {
  if (medium === 'yb-ec') {
    return '请在手机上展码，正在扫描医保电子凭证…';
  }
  if (medium === 'id') {
    return '请将身份证放在读卡器上，正在读卡…';
  }
  return '请将社保卡放在读卡器上，正在读卡…';
}

/** 读凭证失败提示 */
export function outpatientChargeReadCredentialFailHint(
  medium: OutpatientChargeInsuranceMedium,
): string {
  if (medium === 'yb-ec') {
    return '医保预结算须先扫医保码获取人员编号';
  }
  if (medium === 'id') {
    return '医保预结算须先读身份证获取人员编号';
  }
  return '医保预结算须先读卡获取人员编号';
}

/**
 * 选择收费用医保凭证介质。
 * @returns `id` 身份证；`yb-card` 社保卡；`yb-ec` 医保码；取消返回 null
 */
export async function chooseOutpatientChargeInsuranceMedium(): Promise<null | OutpatientChargeInsuranceMedium> {
  const selected = ref<OutpatientChargeInsuranceMedium>('yb-card');
  try {
    await ElMessageBox({
      title: '选择医保凭证',
      message: () =>
        h('div', { style: 'line-height: 1.5' }, [
          h(
            'p',
            { style: 'margin: 0 0 12px; color: var(--el-text-color-regular)' },
            '请选择本次收费使用的医保凭证。医保码时效较短，请展码后立即扫码。',
          ),
          h(
            ElRadioGroup,
            {
              modelValue: selected.value,
              'onUpdate:modelValue': (value: string | number | boolean | undefined) => {
                selected.value = value as OutpatientChargeInsuranceMedium;
              },
            },
            () => [
              h(ElRadioButton, { value: 'id' }, () => '身份证'),
              h(ElRadioButton, { value: 'yb-card' }, () => '社保卡'),
              h(ElRadioButton, { value: 'yb-ec' }, () => '医保码'),
            ],
          ),
        ]),
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      closeOnClickModal: false,
      type: 'info',
    });
    return selected.value;
  } catch {
    return null;
  }
}

async function resolveIdCardCredential(): Promise<null | SocialCard1101Credential> {
  // 与挂号一致：用 invokeIdCardRead 解析 SignBuff；市医保 2207 对 02 明文证件号会解密失败
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
  if (!(card.busiCardInfo ?? '').trim()) {
    ElMessage.warning(
      '读卡未返回卡串（BusiCardInfo），已按回落规则组凭证；若 2201 失败请更新并重启 OpenHIS.Win',
    );
  }
  return {
    credential,
    psnName: (card.psnName ?? '').trim() || undefined,
  };
}

async function resolveEcVoucherCredential(): Promise<{
  credential: SocialCard1101Credential;
  insuplcAdmdvs?: string;
  psnName?: string;
} | null> {
  const snapshot = await readYbEcVoucherSnapshot({ officeName: '门诊收费' });
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

/** 按选定介质读卡/扫码并 1101，返回预结算所需医保上下文；纯自费返回 null。 */
export async function resolveOutpatientChargeInsuranceContext(
  rows: PendingChargeItemRowDto[],
  options: {
    medium: OutpatientChargeInsuranceMedium;
    onPhase?: (phase: OutpatientChargeInsurancePhase) => void;
  },
): Promise<null | OutpatientChargeInsuranceContext> {
  if (!selectionIncludesInsuranceItems(rows)) {
    return null;
  }
  const contractId = resolveInsuranceContractId(rows);
  if (!contractId) {
    ElMessage.warning('所选费用项缺少医保合同，请先转医保或检查账户绑定');
    return null;
  }
  const { medium, onPhase } = options;
  onPhase?.('plugin');
  if (!(await isYbPluginOnline())) {
    ElMessage.warning('医保读卡插件未启动，请先启动读卡服务后再收费');
    return null;
  }
  try {
    onPhase?.('read-card');
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

    onPhase?.('person-1101');
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
      balc: resolvePersonBalc(person),
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
