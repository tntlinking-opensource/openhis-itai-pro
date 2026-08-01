/**
 * 住院医护站「完整预结算」：3101(trig=9) → 对话框 → 2303，仅展示金额不落账。
 * 不要求护士出院；未 PENDING 时走 MID_SETTLE（跳过医保 2402）。
 */
import type { Ref } from 'vue';

import type {
  InpatientSettleQueueItemDto,
  PreSettleInpatientChargesResponse,
} from '#/api/inpatient-settle';

import { ElMessage } from 'element-plus';

import {
  httpPageInpatientSettleQueue,
  httpPreSettleInpatientCharges,
  httpQueryInpatientSettleCharges,
} from '#/api/inpatient-settle';
import {
  runIpDetailAuditGate,
  type DetailAuditRemindDialogExpose,
} from '#/chs/composables/use-detail-audit-gate';
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
  resolveInpatientSettleInsuranceContext,
} from '#/views/inpatient/charge/settle/use-inpatient-settle-insurance';

export interface InpatientClinicalPreSettleParams {
  /** 费用性质展示名（登记账户合同名），用于未入待结队列时反查 contractId */
  contractDisplay?: string;
  encounterBusNo?: string;
  encounterId: string;
  patientId: string;
  /** 就诊状态；空则尝试从结算队列行读取 */
  statusEnum?: string;
}

export interface InpatientClinicalPreSettleResult {
  chargeItemIds: string[];
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
 * 医护站结算模式：已办护士出院 → 出院全结；否则中途试算（不调 2402）。
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
 * 执行医护站完整预结算；用户返回修改或失败时返回 null。
 */
export async function runInpatientClinicalPreSettle(options: {
  dialogRef: Ref<DetailAuditRemindDialogExpose | null | undefined>;
  params: InpatientClinicalPreSettleParams;
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
  const isInsurance = isInpatientInsuranceRegistration(
    contractRow,
    contractMeta.contractCategoryById,
  );

  let insurancePayload: Record<string, string | undefined> = {};
  if (isInsurance) {
    if (!contractId) {
      ElMessage.warning('医保患者缺少合同信息，请检查住院登记');
      return null;
    }
    try {
      const insuranceCtx = await resolveInpatientSettleInsuranceContext(contractId);
      if (insuranceCtx) {
        insurancePayload = {
          contractId: insuranceCtx.contractId,
          insuplcAdmdvs: insuranceCtx.insuplcAdmdvs,
          insutype: insuranceCtx.insutype,
          psnNo: insuranceCtx.psnNo,
          ybCardInfo: insuranceCtx.ybCardInfo,
          ybMediumType: insuranceCtx.ybMediumType,
        };
      } else {
        ElMessage.warning('本机 OpenHIS.Win 未启动，无法完成医保读卡预结算；请先启动插件');
        return null;
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : '医保读卡失败';
      ElMessage.error(message);
      return null;
    }

    try {
      const contractBusNo = getInpatientContractBusNo(contractId, contractMeta);
      const { continued } = await runIpDetailAuditGate({
        params: {
          encounterId,
          chargeItemIds,
          settleMode,
          contractId,
          contractBusNo,
          trigScen: '9',
          psnNo: insurancePayload.psnNo,
          insuplcAdmdvs: insurancePayload.insuplcAdmdvs,
          insutype: insurancePayload.insutype,
        },
        dialogRef: options.dialogRef,
        loadingHint: '医保事前提醒分析中（3101）…',
      });
      if (!continued) {
        ElMessage.info('已按事前提醒返回修改，未继续预结算');
        return null;
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : '事前提醒失败';
      ElMessage.error(message);
      return null;
    }
  }

  const snapshot = await httpPreSettleInpatientCharges({
    encounterId,
    patientId,
    chargeItemIds,
    settleMode,
    ...insurancePayload,
  });
  return { chargeItemIds, snapshot };
}

/** 从预结算快照组装医保分项展示。 */
export function buildClinicalPreSettleInsuranceBreakdown(
  snap: PreSettleInpatientChargesResponse | null | undefined,
): Array<{ key: string; label: string; value: number | string }> {
  if (!snap?.insurancePreSettled) {
    return [];
  }
  return [
    { key: 'medfee', label: '医疗费总额', value: snap.ybMedfeeSumamt ?? 0 },
    { key: 'fund', label: '基金支付', value: snap.ybFundPaySumamt ?? 0 },
    { key: 'acct', label: '个人账户', value: snap.ybAcctPay ?? 0 },
    { key: 'cash', label: '个人现金', value: snap.ybPsnCashPay ?? 0 },
  ];
}
