/**
 * 博思电子票据 — 收费窗 API
 */
import { publicRequestClient } from '#/api/request';

const CASHIER_BASE = '/einvoiceBossCashier';

export interface BossIssueStatusDto {
  invoiceStatus?: string;
  taskStatus?: string;
  amountStatus?: string;
  billNo?: string;
  qrReady?: boolean;
}

export interface BossCashierQrDto {
  billNo?: string;
  billQrCode?: string;
  pictureUrl?: string;
  pictureNetUrl?: string;
}

export function getIssueStatusApi(paymentId: string) {
  return publicRequestClient.post<BossIssueStatusDto>(`${CASHIER_BASE}/getIssueStatus`, {
    paymentId,
  });
}

export function getCashierQrApi(paymentId: string) {
  return publicRequestClient.post<BossCashierQrDto>(`${CASHIER_BASE}/getQr`, { paymentId });
}
