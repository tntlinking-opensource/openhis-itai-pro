import type { PrepayPaymentRecordDto, PrepayQueueItemDto } from '#/api/inpatient-prepay';
import type { InpatientPrepayReceiptPrintSource } from '#/utils/print/mappers/inpatient-prepay-receipt';

import { useUserStore } from '@vben/stores';

import dayjs from 'dayjs';

import { print, PRINT_SCENARIO, scheduleBackgroundPrint } from './runtime';
import { resolvePrintHospitalName } from './resolve-print-hospital-name';

export interface PrintInpatientPrepayReceiptOptions {
  source: InpatientPrepayReceiptPrintSource;
  encounterId?: null | number | string;
  paymentId?: null | number | string;
  jobName?: string;
  /** 伴随主流程打印：不弹打印机选择框 */
  background?: boolean;
}

function firstNonEmpty(values: unknown[]): string {
  for (const item of values) {
    const text = String(item ?? '').trim();
    if (text) {
      return text;
    }
  }
  return '';
}

/** 当前登录操作员展示名（收款人） */
export function resolvePrepayReceiptCashierName(): string {
  const userStore = useUserStore();
  const userInfo = userStore.userInfo as null | Record<string, unknown> | undefined;
  const ext = (userInfo?.extraProperties ?? {}) as Record<string, unknown>;
  return firstNonEmpty([
    userInfo?.realName,
    userInfo?.nickName,
    userInfo?.nickname,
    userInfo?.userName,
    userInfo?.username,
    ext.realName,
    ext.RealName,
  ]);
}

export interface BuildPrepayReceiptSourceFromCollectParams {
  patient: null | PrepayQueueItemDto | undefined;
  amount: number | string;
  payChannelCode: string;
  scanPayType?: string;
  paymentTimeText?: string;
  cashierName?: string;
  hospitalName?: string;
}

/** 收取预交金成功后：用页面数据装配打印入参 */
export function buildPrepayReceiptSourceFromCollect(
  params: BuildPrepayReceiptSourceFromCollectParams,
): InpatientPrepayReceiptPrintSource {
  const patient = params.patient;
  return {
    hospitalName: params.hospitalName?.trim() || resolvePrintHospitalName(),
    departmentName: patient?.organizationName ?? '',
    inpatientNo: patient?.encounterBusNo ?? '',
    patientName: patient?.patientName ?? '',
    patientIdNo: patient?.patientId ? String(patient.patientId) : '',
    paymentTimeText: params.paymentTimeText?.trim() || dayjs().format('YYYY-MM-DD HH:mm'),
    amount: params.amount,
    payChannelCode: params.payChannelCode,
    scanPayType: params.scanPayType,
    cashierName: params.cashierName?.trim() || resolvePrepayReceiptCashierName(),
  };
}

export interface BuildPrepayReceiptSourceFromRecordParams {
  patient: null | PrepayQueueItemDto | undefined;
  record: PrepayPaymentRecordDto;
  /** 覆盖流水渠道；缺省取 record.payChannelCode */
  payChannelCode?: string;
  scanPayType?: string;
  hospitalName?: string;
}

/** 流水补打：用队列患者 + 流水行装配打印入参 */
export function buildPrepayReceiptSourceFromRecord(
  params: BuildPrepayReceiptSourceFromRecordParams,
): InpatientPrepayReceiptPrintSource {
  const { patient, record } = params;
  const tradeTime = String(record.tradeTime ?? '').trim();
  return {
    hospitalName: params.hospitalName?.trim() || resolvePrintHospitalName(),
    departmentName: patient?.organizationName ?? '',
    inpatientNo: patient?.encounterBusNo ?? '',
    patientName: patient?.patientName ?? '',
    patientIdNo: patient?.patientId ? String(patient.patientId) : '',
    paymentTimeText: tradeTime ? dayjs(tradeTime).format('YYYY-MM-DD HH:mm') : tradeTime,
    amount: record.amount,
    payChannelCode: params.payChannelCode ?? record.payChannelCode,
    payMethodText: record.payMethodText,
    scanPayType: params.scanPayType,
    cashierName: String(record.operatorName ?? '').trim() || resolvePrepayReceiptCashierName(),
  };
}

/** 打印住院预交金收据（prepare → hiprint → confirm） */
export async function printInpatientPrepayReceipt(options: PrintInpatientPrepayReceiptOptions) {
  return print({
    scenarioCode: PRINT_SCENARIO.ipPrepayReceiptPrint,
    source: options.source,
    bizRef: {
      scenarioCode: PRINT_SCENARIO.ipPrepayReceiptPrint,
      encounterId: options.encounterId ?? undefined,
      paymentId: options.paymentId ?? undefined,
    },
    jobName: options.jobName ?? '住院预交金收据',
    selectPrinter: options.background ? 'never' : 'auto',
    background: options.background === true,
  });
}

/** 收取成功后异步打印，不阻塞 UI */
export function schedulePrintInpatientPrepayReceipt(
  options: Omit<PrintInpatientPrepayReceiptOptions, 'background'>,
) {
  scheduleBackgroundPrint(() =>
    printInpatientPrepayReceipt({
      ...options,
      background: true,
    }),
  );
}
