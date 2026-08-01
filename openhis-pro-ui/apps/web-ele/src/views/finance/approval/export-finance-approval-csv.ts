/**
 * 财务审批列表导出：生成带 BOM 的 CSV，Excel 可直接打开。
 * 金额列表头随筛选单据类型动态切换，与页面列表一致。
 */
import type { FinanceApprovalDocumentRowDto } from '#/api/finance/approval';

import {
  resolveFinanceApprovalAmountCell,
  resolveFinanceApprovalAmountColumns,
  shouldShowFinanceApprovalLocationSupplier,
} from './finance-approval-amount-columns';

/** CSV 单元格转义。 */
function csvCell(value: unknown): string {
  const raw = value === undefined || value === null ? '' : String(value);
  return `"${raw.replaceAll('"', '""')}"`;
}

/** 审批时间：供应/调价分域择一。 */
function resolveApprovalTime(row: FinanceApprovalDocumentRowDto): string {
  return String(row.approvalTime || row.approveTime || '').trim();
}

/** 制单时间：制单/申请/提交择一。 */
function resolveOccurrenceTime(row: FinanceApprovalDocumentRowDto): string {
  return String(row.occurrenceTime || row.applyTime || row.submitTime || '').trim();
}

/**
 * 下载财务审批列表 CSV。
 *
 * @param rows 导出行
 * @param fileName 文件名（默认财务审批-时间戳.csv）
 * @param typeEnum 当前筛选单据类型（驱动金额列表头）
 */
export function downloadFinanceApprovalCsv(
  rows: FinanceApprovalDocumentRowDto[],
  fileName?: string,
  typeEnum?: string,
): void {
  const amountColumns = resolveFinanceApprovalAmountColumns(typeEnum);
  const showLocationSupplier = shouldShowFinanceApprovalLocationSupplier(typeEnum);
  const header = [
    '单据号',
    '单据类型',
    '审批状态',
    ...(showLocationSupplier ? (['库房', '供应商'] as const) : []),
    '品类',
    '制单人',
    '制单时间',
    ...amountColumns.map((col) => col.label),
    '审批人',
    '审批时间',
    '驳回原因',
  ];
  const lines = [header.map(csvCell).join(',')];
  for (const row of rows) {
    const amountCells = amountColumns.map((col) => resolveFinanceApprovalAmountCell(row, col.key));
    lines.push(
      [
        row.busNo,
        row.typeEnumText,
        row.statusEnumText,
        ...(showLocationSupplier ? [row.locationName, row.supplierName] : []),
        row.itemTableText,
        row.applicantName,
        resolveOccurrenceTime(row),
        ...amountCells,
        row.approverName,
        resolveApprovalTime(row),
        row.rejectReason,
      ]
        .map(csvCell)
        .join(','),
    );
  }
  const bom = '\uFEFF';
  const blob = new Blob([bom + lines.join('\n')], {
    type: 'text/csv;charset=utf-8;',
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  const stamp = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  const defaultName = `财务审批_${stamp.getFullYear()}${pad(stamp.getMonth() + 1)}${pad(stamp.getDate())}.csv`;
  link.href = url;
  link.download = fileName?.trim() || defaultName;
  link.click();
  URL.revokeObjectURL(url);
}
