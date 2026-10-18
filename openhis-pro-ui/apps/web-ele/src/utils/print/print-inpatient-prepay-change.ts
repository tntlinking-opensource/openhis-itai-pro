import type { PrepayPaymentRecordDto, PrepayQueueItemDto } from '#/api/inpatient-prepay';
import type {
  InpatientPrepayChangeKind,
  InpatientPrepayChangePrintSource,
} from '#/utils/print/mappers/inpatient-prepay-change';

import {
  buildPrepayReceiptSourceFromCollect,
  buildPrepayReceiptSourceFromRecord,
} from './print-inpatient-prepay-receipt';
import { print, PRINT_SCENARIO, scheduleBackgroundPrint } from './runtime';

export interface PrintInpatientPrepayChangeOptions {
  source: InpatientPrepayChangePrintSource;
  encounterId?: null | number | string;
  paymentId?: null | number | string;
  jobName?: string;
  /** 伴随主流程打印：不弹打印机选择框 */
  background?: boolean;
}

function toChangeSource(
  base: ReturnType<typeof buildPrepayReceiptSourceFromCollect>,
  changeKind: InpatientPrepayChangeKind,
): InpatientPrepayChangePrintSource {
  return {
    ...base,
    changeKind,
    documentTitle: '住院预交金找补收据',
  };
}

export interface BuildPrepayChangeSourceFromCollectParams {
  patient: null | PrepayQueueItemDto | undefined;
  amount: number | string;
  payChannelCode: string;
  changeKind: InpatientPrepayChangeKind;
  scanPayType?: string;
  payMethodText?: string;
  paymentTimeText?: string;
  cashierName?: string;
  hospitalName?: string;
}

/** 收取或退费成功后：用页面数据装配找补收据 */
export function buildPrepayChangeSourceFromCollect(
  params: BuildPrepayChangeSourceFromCollectParams,
): InpatientPrepayChangePrintSource {
  return toChangeSource(
    {
      ...buildPrepayReceiptSourceFromCollect(params),
      payMethodText: params.payMethodText,
    },
    params.changeKind,
  );
}

export interface BuildPrepayChangeSourceFromRecordParams {
  patient: null | PrepayQueueItemDto | undefined;
  record: PrepayPaymentRecordDto;
  payChannelCode?: string;
  scanPayType?: string;
  hospitalName?: string;
}

/** 流水补打：收取为补，退预交金（UN_PAY）为退 */
export function buildPrepayChangeSourceFromRecord(
  params: BuildPrepayChangeSourceFromRecordParams,
): InpatientPrepayChangePrintSource {
  const changeKind: InpatientPrepayChangeKind =
    params.record.paymentEnum === 'UN_PAY' ? 'refund' : 'collect';
  return toChangeSource(buildPrepayReceiptSourceFromRecord(params), changeKind);
}

/** 打印住院预交金找补收据（三联单二等分，针式联单定高） */
export async function printInpatientPrepayChange(options: PrintInpatientPrepayChangeOptions) {
  return print({
    scenarioCode: PRINT_SCENARIO.ipPrepayChangePrint,
    source: options.source,
    bizRef: {
      scenarioCode: PRINT_SCENARIO.ipPrepayChangePrint,
      encounterId: options.encounterId ?? undefined,
      paymentId: options.paymentId ?? undefined,
    },
    jobName: options.jobName ?? '住院预交金找补收据',
    selectPrinter: options.background ? 'never' : 'auto',
    background: options.background === true,
    repeatPrintData: true,
    copies: 1,
  });
}

/** 收取或退费成功后异步打印，不阻塞 UI */
export function schedulePrintInpatientPrepayChange(
  options: Omit<PrintInpatientPrepayChangeOptions, 'background'>,
) {
  scheduleBackgroundPrint(() =>
    printInpatientPrepayChange({
      ...options,
      background: true,
    }),
  );
}
