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
  /** 1101 参保人身份证号（BR-OPC-035，供预结算回传） */
  payerCertno?: string;
  /** 1101 参保人姓名（BR-OPC-035，供预结算回传） */
  payerPsnName?: string;
  psnNo: string;
  ybCardInfo: string;
  ybMediumType: string;
}

/**
 * 就诊患者身份（用于读卡后与 1101 结果比对，BR-OPC-035）。
 */
export interface OutpatientChargePatientIdentity {
  /** 患者身份证号；可空（临时建档） */
  idCard?: null | string;
  /** 患者姓名 */
  patientName?: null | string;
}

/**
 * 断言 1101 读卡人与当前就诊患者一致；不一致时提示并返回 false。
 *
 * @param person 1101 人员信息
 * @param patient 当前队列就诊患者
 * @returns 一致 true；不一致 false（已 ElMessage.error）
 */
function assertPayerMatchesChargePatient(
  person: { certno?: string; psnName?: string },
  patient: OutpatientChargePatientIdentity,
): boolean {
  const patientIdCard = String(patient.idCard ?? '')
    .trim()
    .toUpperCase();
  const patientName = String(patient.patientName ?? '').trim();
  const payerCertno = String(person.certno ?? '')
    .trim()
    .toUpperCase();
  const payerName = String(person.psnName ?? '').trim();
  // Gate：就诊无可比对身份时拒绝，避免静默放行
  if (!patientIdCard && !patientName) {
    ElMessage.error('当前就诊缺少患者身份证号与姓名，无法核验医保读卡人');
    return false;
  }
  // Gate：有身份证时以证件为准（防串账）
  if (patientIdCard) {
    if (!payerCertno) {
      ElMessage.error('读卡未返回身份证号，请重新读取当前患者医保凭证后再收费');
      return false;
    }
    if (patientIdCard !== payerCertno) {
      const patientLabel = patientName || '当前患者';
      const payerLabel = payerName || '读卡人员';
      ElMessage.error(
        `医保读卡人「${payerLabel}」与就诊患者「${patientLabel}」不一致，请重新读取当前患者的医保凭证后再收费`,
      );
      return false;
    }
    return true;
  }
  // Gate：无证号档案回落姓名
  if (!payerName || patientName !== payerName) {
    const patientLabel = patientName || '当前患者';
    const payerLabel = payerName || '读卡人员';
    ElMessage.error(
      `医保读卡人「${payerLabel}」与就诊患者「${patientLabel}」不一致，请重新读取当前患者的医保凭证后再收费`,
    );
    return false;
  }
  return true;
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
 * 收费行绑定诊断串的首位（主诊断）。雪花主键保持字符串，避免精度丢失。
 */
export function primaryEncounterDiagnosisId(row: PendingChargeItemRowDto): string {
  const raw = String(row.encounterDiagnosisIds ?? '');
  const first = raw.split(',')[0] ?? '';
  return first.trim();
}

/** 医保/工伤记账行。自费、公费不参与病种分次。 */
function isInsuranceSettleRow(row: PendingChargeItemRowDto): boolean {
  const contractCat = String(row.contractCategoryCode ?? '').trim();
  const feeNature = String(row.feeNatureDisplay ?? '').trim();
  const isSelfOrPublic =
    isSelfPayContractCategory(contractCat) ||
    isPublicChargeContractCategory(contractCat) ||
    feeNature.includes('自费');
  if (isSelfOrPublic) {
    return false;
  }
  return (
    isInsuranceContractCategory(contractCat) ||
    isWorkInjuryContractCategory(contractCat) ||
    feeNature.includes('医保') ||
    feeNature.includes('工伤')
  );
}

function insuranceSettleAccountId(row: PendingChargeItemRowDto): string {
  return String(row.accountId ?? '').trim() || '0';
}

/**
 * 须按病种分次结算的医疗类别：门诊慢特病、门诊慢病、门诊特病、特药。
 * 普通门诊等同类别一次结算，不按诊断拆组。
 */
const DISEASE_SETTLE_MED_TYPES = new Set(['14', '140104', '140201', '15']);

/**
 * 该就诊类型是否按病种分次。
 *
 * @param medType 医疗类别码
 */
function requiresPerDiseaseSettle(medType: string): boolean {
  return DISEASE_SETTLE_MED_TYPES.has(medType);
}

/**
 * 已绑诊断的医保分组键：账户 + 就诊类型；慢特病再加诊断。
 * 普通门诊忽略诊断，同账户同类别合成一组。
 * 未绑诊断的医保行返回空串，不单独成组。
 * 自费/公费行返回空串，不计入医保组数。
 */
export function resolveInsuranceSettleGroupKey(row: PendingChargeItemRowDto): string {
  if (!isInsuranceSettleRow(row)) {
    return '';
  }
  const diseKey = primaryEncounterDiagnosisId(row);
  if (!diseKey) {
    return '';
  }
  const accountId = insuranceSettleAccountId(row);
  const medType = String(row.medTypeCode ?? '').trim() || '11';
  if (!requiresPerDiseaseSettle(medType)) {
    return `${accountId}|${medType}|`;
  }
  return `${accountId}|${medType}|${diseKey}`;
}

/** 整账户都没有诊断时，无诊断医保行合成普通门诊一组。 */
function blankInsuranceGroupKey(accountId: string): string {
  return `${accountId}|11|`;
}

/**
 * 分组键的账户段。键格式为账户|就诊类型|诊断。
 *
 * @param key 医保结算分组键
 */
function insuranceSplitKeyAccount(key: string): string {
  const cut = key.indexOf('|');
  if (cut < 0) {
    return key;
  }
  return key.slice(0, cut);
}

/**
 * 医保结算分组键列表（账户|medType|诊断）：供组数统计。
 * 普通门诊有诊断的行按就诊类型并组；慢特病按诊断拆。
 * 同账户无诊断行不另开一组。
 */
function listInsuranceSplitKeys(rows: PendingChargeItemRowDto[]): string[] {
  const diagnosedKeys: string[] = [];
  const diagnosedSeen = new Set<string>();
  const diagnosedAccounts = new Set<string>();
  const blankOnlyAccounts: string[] = [];
  const blankOnlySeen = new Set<string>();
  for (const row of rows) {
    if (!isInsuranceSettleRow(row)) {
      continue;
    }
    const accountId = insuranceSettleAccountId(row);
    const diagnosedKey = resolveInsuranceSettleGroupKey(row);
    if (diagnosedKey) {
      diagnosedAccounts.add(accountId);
      if (!diagnosedSeen.has(diagnosedKey)) {
        diagnosedSeen.add(diagnosedKey);
        diagnosedKeys.push(diagnosedKey);
      }
      continue;
    }
    if (!blankOnlySeen.has(accountId)) {
      blankOnlySeen.add(accountId);
      blankOnlyAccounts.push(accountId);
    }
  }
  const keys = [...diagnosedKeys];
  for (const accountId of blankOnlyAccounts) {
    if (diagnosedAccounts.has(accountId)) {
      continue;
    }
    keys.push(blankInsuranceGroupKey(accountId));
  }
  return keys;
}

/**
 * 按出现顺序列出医保行上的病种名。同一诊断只出现一次。
 * 未绑诊断的行不记成「未填病种」。
 */
export function collectInsuranceDiseaseLabels(rows: PendingChargeItemRowDto[]): string[] {
  const seen = new Set<string>();
  const labels: string[] = [];
  for (const row of rows) {
    const key = resolveInsuranceSettleGroupKey(row);
    if (!key || seen.has(key)) {
      continue;
    }
    seen.add(key);
    const name = String(row.diseName ?? '').trim();
    labels.push(name || '未填病种');
  }
  return labels;
}

/**
 * 多个慢特病不能一次勾选时的提示。普通门诊多诊断不提示。只有一个病种时返回空串。
 *
 * @param labels 病种名列表
 */
export function buildPerDiseaseSettleHint(labels: string[]): string {
  if (labels.length <= 1) {
    return '';
  }
  const parts: string[] = [];
  for (const label of labels) {
    parts.push(`「${label}」单独结一笔`);
  }
  return `多个慢特病不能一次结算：${parts.join('，')}。请只勾选其中一个病种。`;
}

/**
 * 勾选跨了多个病种时，只保留一个病种的行，自费行仍保留。
 * 同账户未绑诊断的医保行跟着留下的病种，不单独剔出。
 * preferredKey 是改勾选前已选中的病种；全选时传空，则保留列表中的第一个病种。
 *
 * @param rows         展开后的勾选行
 * @param preferredKey 优先保留的分组键
 */
export function keepSingleInsuranceDiseaseRows<T extends PendingChargeItemRowDto>(
  rows: T[],
  preferredKey: string,
): T[] {
  const keys = listInsuranceSplitKeys(rows);
  if (keys.length <= 1) {
    return rows;
  }
  let keepKey = keys[0] ?? '';
  if (preferredKey && keys.includes(preferredKey)) {
    keepKey = preferredKey;
  }
  const keepAccount = insuranceSplitKeyAccount(keepKey);
  return rows.filter((row) => {
    if (!isInsuranceSettleRow(row)) {
      return true;
    }
    const diagnosedKey = resolveInsuranceSettleGroupKey(row);
    if (diagnosedKey) {
      return diagnosedKey === keepKey;
    }
    return insuranceSettleAccountId(row) === keepAccount;
  });
}

/**
 * 当前勾选里若只有一个医保结算组，返回其分组键；否则返回空串。
 *
 * @param rows 勾选明细
 */
export function singleSelectedInsuranceGroupKey(rows: PendingChargeItemRowDto[]): string {
  const keys = listInsuranceSplitKeys(rows);
  if (keys.length === 1) {
    return keys[0] ?? '';
  }
  return '';
}

/**
 * 勾选明细对应的医保结算组数（>1 须分次结算）。
 * 同账户未绑诊断的行不另计一组。
 */
export function countInsuranceSettleGroups(rows: PendingChargeItemRowDto[]): number {
  return listInsuranceSplitKeys(rows).length;
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
      '读卡未返回卡串（BusiCardInfo），已按回落规则组凭证；若 2201 失败请更新并重启 Zxplat.Win',
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
    /** 当前收费队列就诊患者（BR-OPC-035 读卡核验） */
    patient?: OutpatientChargePatientIdentity;
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
  const { medium, onPhase, patient } = options;
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
    // Gate：BR-OPC-035 — 读卡人须等于当前就诊患者，扫错卡当场拦截
    if (patient && !assertPayerMatchesChargePatient(person, patient)) {
      return null;
    }
    const payerCertno = String(person.certno ?? '').trim() || undefined;
    const payerPsnName =
      String(person.psnName ?? '').trim() || (psnName ? psnName.trim() : undefined);
    return {
      balc: resolvePersonBalc(person),
      contractId,
      insuplcAdmdvs: person.insuplcAdmdvs || issuingAdmdvs,
      insutype: person.insutype,
      medium,
      payerCertno,
      payerPsnName,
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
