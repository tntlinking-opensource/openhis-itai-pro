/**
 * 住院医护站「完整预结算」：3101(trig=9) 不阻断 → 2301 → 2303，仅展示金额不落账。
 * 有事前提醒时并入预结算弹窗页签，不等待 3103 处置。
 * 不读卡：人员编号与险种由后端按入院资料回填，2303 不传就诊凭证号。
 * 未护士出院传 MID_SETTLE（跳过 2402、放宽就诊态）；平台 mid_setl_flag 由后端写死为 0。
 */
import type { Ref } from 'vue';

import type {
  InpatientSettleChargeLineDto,
  InpatientSettleQueueItemDto,
  PreSettleInpatientChargesResponse,
  UploadInpatientFeeDetailsBatchRequest,
} from '#/api/inpatient-settle';
import type { AuditViolation } from '#/chs/api/detail-audit-try';
import type { InsuranceProgressReporter } from '#/chs/composables/use-insurance-progress-dialog';

import { nextTick } from 'vue';

import { ElMessage } from 'element-plus';

import {
  httpPageInpatientSettleQueue,
  httpPreSettleInpatientCharges,
  httpQueryInpatientSettleCharges,
  httpUploadInpatientFeeDetailsBatch,
} from '#/api/inpatient-settle';
import { analyzeIpGateApi, gateHasViolations } from '#/chs/api/detail-audit-gate';
import { type DetailAuditRemindDialogExpose } from '#/chs/composables/use-detail-audit-gate';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  getInpatientContractBusNo,
  loadInpatientRegisterContractMeta,
} from '#/views/inpatient/charge/register/services/inpatient-register-contract-meta';
import { loadRegisterFormInit } from '#/views/outpatient/charge/register/services/register.service';
import {
  ENCOUNTER_STATUS_PENDING,
  INPATIENT_SETTLE_MODE,
  SETTLE_PRESENTATION_MODE,
  SETTLE_QUEUE_TAB,
} from '#/views/inpatient/charge/settle/inpatient-settle.constants';
import {
  isInpatientInsuranceRegistration,
  isZeroAmountDischargeFullSettle,
} from '#/views/inpatient/charge/settle/use-inpatient-settle-insurance';

export interface InpatientClinicalPreSettleParams {
  /** 费用性质展示名（登记账户合同名），用于未入待结队列时反查 contractId */
  contractDisplay?: string;
  encounterBusNo?: string;
  encounterId: string;
  /** 入院险种（310 职工 / 390 居民）；有值则必须走 2303 看报销 */
  insutype?: string;
  patientId: string;
  /** 就诊状态；空则尝试从结算队列行读取 */
  statusEnum?: string;
}

export interface InpatientClinicalPreSettleResult {
  /** 3101 失败说明；成功且无违规时为空 */
  auditRemindNote: string;
  /** 3101 违规提醒；无则空数组。不阻断后续预结算 */
  auditViolations: AuditViolation[];
  chargeItemIds: string[];
  /** 本批试算费用行（弹窗「结算费用」表） */
  chargeLines: InpatientSettleChargeLineDto[];
  snapshot: PreSettleInpatientChargesResponse;
}

/** 结算待结队列解析当前就诊行（取 contractId / status）。 */
async function resolvePendingSettleQueueRow(
  encounterId: string,
  encounterBusNo?: string,
): Promise<InpatientSettleQueueItemDto | null> {
  const eid = preserveApiIdForRequest(encounterId);
  if (!eid) {
    return null;
  }
  const keyword = String(encounterBusNo ?? '').trim() || undefined;
  const queueRes = await httpPageInpatientSettleQueue({
    keyword,
    settleQueueTab: SETTLE_QUEUE_TAB.PENDING_SETTLE,
    pageNo: 1,
    pageSize: 50,
  });
  const rows = parsePagedRowsFromApiData<InpatientSettleQueueItemDto>(queueRes).content;
  return (
    rows.find((r) => preserveApiIdForRequest(r.encounterId) === eid) ??
    rows.find((r) => String(r.encounterBusNo ?? '').trim() === keyword) ??
    null
  );
}

/** 入院账户或读卡缓存已有险种时，完整预结算要看医保报销，不能按自费轧差。 */
function hasAdmissionInsutype(insutype?: string): boolean {
  return String(insutype ?? '').trim().length > 0;
}

/** 按费用性质展示名匹配合同主键（未入待结队列时的回落）。 */
async function resolveContractIdByDisplayName(
  contractDisplay?: string,
): Promise<string | undefined> {
  const display = String(contractDisplay ?? '').trim();
  if (!display || display === '—') {
    return undefined;
  }
  const init = await loadRegisterFormInit({});
  const matched = (init.contractOptions ?? []).find(
    (opt) => String(opt.label ?? '').trim() === display,
  );
  return preserveApiIdForRequest(matched?.value) || undefined;
}

/**
 * 医护站结算模式：PENDING → 出院全结；否则试算（跳过 2402，平台 mid 标志后端写死 0）。
 */
function resolveClinicalSettleMode(
  statusEnum: string,
  chargeItemIds: string[],
): string | undefined {
  if (chargeItemIds.length === 0) {
    return statusEnum === ENCOUNTER_STATUS_PENDING
      ? INPATIENT_SETTLE_MODE.DISCHARGE_FULL
      : undefined;
  }
  if (statusEnum === ENCOUNTER_STATUS_PENDING) {
    return INPATIENT_SETTLE_MODE.DISCHARGE_FULL;
  }
  return INPATIENT_SETTLE_MODE.MID_SETTLE;
}

/**
 * 循环调用单批 2301，直到待传为空；进度弹窗显示「已上传 x/y」。
 */
export async function uploadInpatientFeeDetailsWithProgress(options: {
  payload: Omit<UploadInpatientFeeDetailsBatchRequest, 'mdtrtId'>;
  progress?: InsuranceProgressReporter;
  /** 费用上传步骤下标 */
  stepIndex: number;
}): Promise<void> {
  const { payload, progress, stepIndex } = options;
  if (!payload.chargeItemIds.length) {
    return;
  }
  let mdtrtId: string | undefined;
  let uploaded = 0;
  let total = 0;
  let safety = 0;
  const maxRounds = 50;

  progress?.setStep(stepIndex, '正在上传费用明细（2301）…');

  while (safety < maxRounds) {
    safety += 1;
    const batch = await httpUploadInpatientFeeDetailsBatch({
      ...payload,
      mdtrtId,
    });
    const batchUploaded = Number(batch.batchUploaded ?? 0);
    const remainingAfter = Number(batch.remainingAfter ?? 0);
    if (total <= 0) {
      total = Math.max(0, batchUploaded + remainingAfter);
    }
    uploaded = Math.min(total, uploaded + Math.max(0, batchUploaded));
    if (batch.mdtrtId) {
      mdtrtId = String(batch.mdtrtId);
    }
    if (total > 0) {
      progress?.setCount(uploaded, total);
      progress?.setStep(stepIndex, `费用明细上传中（2301）：已上传 ${uploaded}/${total}`);
    }
    if (batch.done || batchUploaded <= 0) {
      break;
    }
  }
  if (total > 0) {
    progress?.setCount(total, total);
    progress?.setStep(stepIndex, `费用明细上传完成：${total}/${total}`);
  }
}

/**
 * 执行医护站完整预结算；用户返回修改或失败时返回 null。
 */
export async function runInpatientClinicalPreSettle(options: {
  dialogRef: Ref<DetailAuditRemindDialogExpose | null | undefined>;
  params: InpatientClinicalPreSettleParams;
  /** 对齐门诊：读卡→1101→3101→2303 过程进度 */
  progress?: InsuranceProgressReporter;
}): Promise<InpatientClinicalPreSettleResult | null> {
  const encounterId = preserveApiIdForRequest(options.params.encounterId);
  const patientId = preserveApiIdForRequest(options.params.patientId);
  if (!encounterId || !patientId) {
    ElMessage.warning('请先选择患者');
    return null;
  }

  const queueRow = await resolvePendingSettleQueueRow(encounterId, options.params.encounterBusNo);
  const statusEnum =
    String(options.params.statusEnum ?? '').trim() || String(queueRow?.statusEnum ?? '').trim();

  const charges = await httpQueryInpatientSettleCharges({
    encounterId,
    patientId,
    presentationMode: SETTLE_PRESENTATION_MODE.DETAIL,
  });
  const chargeItemIds = (charges.lines ?? [])
    .map((line) => preserveApiIdForRequest(line.chargeItemId))
    .filter(Boolean) as string[];
  // 弹窗展示用：保留本批费用行快照（与 chargeItemIds 同序口径）
  const chargeLines = charges.lines ?? [];

  const zeroAmount = isZeroAmountDischargeFullSettle(statusEnum, chargeItemIds, chargeItemIds);
  if (chargeItemIds.length === 0 && !zeroAmount) {
    ElMessage.warning('暂无待结算费用');
    return null;
  }

  const settleMode = resolveClinicalSettleMode(statusEnum, chargeItemIds);
  if (!settleMode) {
    ElMessage.warning('暂无待结算费用');
    return null;
  }

  const contractMeta = await loadInpatientRegisterContractMeta();
  let contractId = preserveApiIdForRequest(queueRow?.contractId);
  if (!contractId) {
    contractId = await resolveContractIdByDisplayName(options.params.contractDisplay);
  }
  const contractRow = contractId
    ? ({ ...(queueRow ?? {}), contractId } as InpatientSettleQueueItemDto)
    : queueRow;
  const registrationInsurance = isInpatientInsuranceRegistration(
    contractRow,
    contractMeta.contractCategoryById,
  );
  const admissionInsured = hasAdmissionInsutype(options.params.insutype);
  // 名称反查到自费合同时不能带上，否则后端不会回落入院医保户
  const insuranceContractId = registrationInsurance ? contractId : undefined;
  const isInsurance = registrationInsurance || admissionInsured;
  const insutype = String(options.params.insutype ?? '').trim();
  const insurancePayload = isInsurance
    ? {
        ...(insuranceContractId ? { contractId: insuranceContractId } : {}),
        cardlessTrial: true as const,
        ...(insutype ? { insutype } : {}),
      }
    : {};

  const progress = options.progress;
  let progressOpened = false;
  let auditViolations: AuditViolation[] = [];
  let auditRemindNote = '';

  try {
    if (isInsurance) {
      // 无卡试算：不选介质、不读卡、不调 1101。合同主键缺失时由后端按入院医保户回填。
      progress?.open(
        '完整预结算处理中',
        [
          { key: 'detail-audit', label: '医保事前提醒（3101）' },
          { key: 'fee-upload', label: '费用明细上传（2301）' },
          { key: 'pre-settle', label: '医保预结算通信（2303）' },
          { key: 'open-dialog', label: '打开预结算结果' },
        ],
        '正在准备…',
      );
      progressOpened = true;
      await nextTick();

      if (insuranceContractId) {
        try {
          progress?.setStep(0, '医保事前提醒分析中（3101），请稍候…');
          const contractBusNo = getInpatientContractBusNo(insuranceContractId, contractMeta);
          const gate = await analyzeIpGateApi({
            encounterId,
            chargeItemIds,
            settleMode,
            contractId: insuranceContractId,
            contractBusNo,
            trigScen: '9',
          });
          if (gateHasViolations(gate)) {
            auditViolations = gate.analyzeResult?.violations ?? [];
          }
          progress?.setStep(
            0,
            auditViolations.length > 0
              ? `事前提醒 ${auditViolations.length} 条，继续预结算…`
              : '事前提醒无违规，继续预结算…',
          );
        } catch (error) {
          auditRemindNote = error instanceof Error ? error.message : '事前提醒失败';
          progress?.setStep(0, '事前提醒未完成，继续预结算…');
        }
      } else {
        progress?.setStep(0, '入院险种已确认，按医保账户继续预结算…');
      }

      try {
        await uploadInpatientFeeDetailsWithProgress({
          payload: {
            encounterId,
            patientId,
            chargeItemIds,
            settleMode,
            ...insurancePayload,
          },
          progress,
          stepIndex: 1,
        });
      } catch (error) {
        const message = error instanceof Error ? error.message : '费用明细上传失败';
        ElMessage.error(message);
        return null;
      }

      progress?.clearCount();
      progress?.setStep(2, '医保预结算通信中（2303），请稍候…');
    } else {
      progress?.open(
        '完整预结算处理中',
        [
          { key: 'pre-settle', label: '生成预结算试算金额' },
          { key: 'open-dialog', label: '打开预结算结果' },
        ],
        '正在计算应收…',
      );
      progressOpened = true;
      await nextTick();
    }

    const snapshot = await httpPreSettleInpatientCharges({
      encounterId,
      patientId,
      chargeItemIds,
      settleMode,
      ...insurancePayload,
    });
    progress?.setStep(isInsurance ? 3 : 1, '预结算完成，即将打开结果窗口…');
    return { chargeItemIds, chargeLines, snapshot, auditViolations, auditRemindNote };
  } finally {
    if (progressOpened) {
      progress?.close();
    }
  }
}

/**
 * 预交金冲抵额 O：优先后端 `depositOffsetAmount`（自费=医保同一套）。
 */
export function resolveClinicalPreSettlePrepayOffset(
  snap: PreSettleInpatientChargesResponse | null | undefined,
): number {
  if (!snap) {
    return 0;
  }
  if (snap.depositOffsetAmount != null && snap.depositOffsetAmount !== '') {
    const offset = Number(snap.depositOffsetAmount);
    return Number.isFinite(offset) ? Math.max(0, offset) : 0;
  }
  // 旧响应无冲抵字段：自费用应收 − 渠道实付回推；医保不得臆造
  if (snap.insurancePreSettled) {
    return 0;
  }
  const payable = Number(snap.displayAmount ?? 0);
  const safePayable = Number.isFinite(payable) ? Math.max(0, payable) : 0;
  const personal =
    snap.personalCashPayable != null && snap.personalCashPayable !== ''
      ? Number(snap.personalCashPayable)
      : Number(snap.receivableTotal ?? 0);
  const safePersonal = Number.isFinite(personal) ? Math.max(0, personal) : 0;
  return Math.max(0, safePersonal - safePayable);
}

/**
 * 渠道实付 C：预结算 `displayAmount`；无快照时勿用 balanceHint。
 */
export function resolveClinicalPreSettleChannelPayable(
  snap: PreSettleInpatientChargesResponse | null | undefined,
): number {
  if (!snap || snap.displayAmount == null || snap.displayAmount === '') {
    return 0;
  }
  const n = Number(snap.displayAmount);
  return Number.isFinite(n) ? Math.max(0, n) : 0;
}

/**
 * 个人现金应付 P：优先 `personalCashPayable`，医保回落 `ybPsnCashPay`。
 */
export function resolveClinicalPreSettlePersonalCashPayable(
  snap: PreSettleInpatientChargesResponse | null | undefined,
): number | null {
  if (!snap) {
    return null;
  }
  if (snap.personalCashPayable != null && snap.personalCashPayable !== '') {
    const n = Number(snap.personalCashPayable);
    return Number.isFinite(n) ? n : null;
  }
  if (snap.insurancePreSettled && snap.ybPsnCashPay != null && snap.ybPsnCashPay !== '') {
    const n = Number(snap.ybPsnCashPay);
    return Number.isFinite(n) ? n : null;
  }
  return null;
}

/**
 * 从预结算快照组装费用明细（对齐门诊确认收费弹窗基金分项 key）。
 * 冲抵 O / 应退 / 渠道实付 C 与款台同一套 R1～R7。
 */
export function buildClinicalPreSettleInsuranceBreakdown(
  snap: PreSettleInpatientChargesResponse | null | undefined,
): Array<{ key: string; label: string; value: number }> {
  if (!snap?.insurancePreSettled) {
    return [];
  }
  const prepayNet = Number(snap.prepayNetBalance ?? 0);
  const offset = resolveClinicalPreSettlePrepayOffset(snap);
  const refundRaw = Number(snap.prepayRefundAmount ?? 0);
  const refund = Number.isFinite(refundRaw) ? Math.max(0, refundRaw) : 0;
  const personal =
    resolveClinicalPreSettlePersonalCashPayable(snap) ?? Number(snap.ybPsnCashPay ?? 0);
  const channel = resolveClinicalPreSettleChannelPayable(snap);
  const refundLabel = snap.prepayFullRefund ? '预交金全额退现金' : '结算后退现金';
  return [
    { key: 'fundPay', label: '基金支付合计', value: Number(snap.ybFundPaySumamt ?? 0) },
    { key: 'poolingPay', label: '基本医保统筹', value: Number(snap.ybHifpPay ?? 0) },
    { key: 'otherPay', label: '其他支出', value: Number(snap.ybOthPay ?? 0) },
    { key: 'accountPay', label: '个人账户支付', value: Number(snap.ybAcctPay ?? 0) },
    { key: 'deductibleLine', label: '起付线', value: Number(snap.ybActPayDedc ?? 0) },
    { key: 'cvlservPay', label: '公务员医疗补助', value: Number(snap.ybCvlservPay ?? 0) },
    { key: 'cashPay', label: '医保个人现金', value: personal },
    {
      key: 'prepayBalance',
      label: '预交金余额',
      value: Number.isFinite(prepayNet) ? prepayNet : 0,
    },
    { key: 'prepayOffset', label: '预交金冲抵', value: offset },
    { key: 'prepayRefund', label: refundLabel, value: refund },
    { key: 'finalReceivable', label: '应付个人现金', value: channel },
  ];
}
