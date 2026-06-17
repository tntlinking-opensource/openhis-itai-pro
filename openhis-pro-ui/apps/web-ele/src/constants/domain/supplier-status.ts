/**
 * 供应商状态编码，与后端 {@code SupplierStatus#getCode()} 一致。
 */
export const SupplierStatusCode = {
  DRAFT: 'DRAFT',
  ACTIVE: 'ACTIVE',
  SUSPENDED: 'SUSPENDED',
  DELETED: 'DELETED',
} as const;

export type SupplierStatusCodeValue = (typeof SupplierStatusCode)[keyof typeof SupplierStatusCode];

const STATUS_LABEL: Record<string, string> = {
  [SupplierStatusCode.DRAFT]: '草稿',
  [SupplierStatusCode.ACTIVE]: '已启用',
  [SupplierStatusCode.SUSPENDED]: '已暂停',
  [SupplierStatusCode.DELETED]: '已删除',
};

/** 列表/详情展示文案（优先后端 enumText） */
export function supplierStatusLabel(statusEnum?: string | null, enumText?: string | null): string {
  if (enumText?.trim()) {
    return enumText.trim();
  }
  if (!statusEnum) {
    return '-';
  }
  return STATUS_LABEL[statusEnum] ?? statusEnum;
}

/** 是否处于启用态（可操作采购等业务） */
export function isSupplierActive(statusEnum?: string | null): boolean {
  return statusEnum === SupplierStatusCode.ACTIVE;
}
