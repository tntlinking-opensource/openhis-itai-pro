import type { ProductColumn } from './advice.types';

/** 与目录、暂存批次定价口径一致（AS BR-ORD-001 / SDD） */
export const ADVICE_PRICING_SOURCE_CODE = 'retailPrice';

export const MEDICATION_DEFINITION_TABLE = 'med_medication_definition';
export const ACTIVITY_DEFINITION_TABLE = 'wkf_activity_definition';
export const DEVICE_DEFINITION_TABLE = 'adm_device_definition';
export const MEDICATION_REQUEST_TABLE = 'med_medication_request';
export const SERVICE_REQUEST_TABLE = 'wkf_service_request';

/** IF-DOR-35 中草药 medication_category_code；开立医嘱选品需排除 */
export const HERBAL_MEDICATION_CATEGORY_CODE = '4';

/** 开立医嘱目录检索默认排除的药品分类 */
export const WESTERN_ADVICE_CATALOG_EXCLUDE_MEDICATION_CATEGORY_CODES = [
  HERBAL_MEDICATION_CATEGORY_CODE,
] as const;

/** 列表未返回 `productTableName` 时，按 `resourceType` 回退（与域表名对齐） */
export const ADVICE_ROW_PRODUCT_TABLE_FALLBACK: Record<string, string> = {
  MEDICATION: MEDICATION_DEFINITION_TABLE,
  SERVICE: 'wkf_service_request',
  DEVICE: 'wkf_device_request',
};

/** 与 `onAdviceTabDocumentKeydown` 内排除弹层用选择器保持一致 */
export const ADVICE_ESC_OVERLAY_SELECTOR =
  '.el-overlay-dialog, .el-message-box__wrapper, [aria-modal="true"]';

/** 医嘱目录选药表格列（西药编辑卡） */
export const ADVICE_PRODUCT_COLUMNS: ProductColumn[] = [
  { prop: 'code', label: '项目编码', width: 120 },
  { prop: 'name', label: '项目名称', width: 200 },
  { prop: 'category', label: '类别', width: 72 },
  { prop: 'spec', label: '规格', width: 100 },
  { prop: 'availableQuantity', label: '库存数量', width: 108 },
  { prop: 'price', label: '单价', width: 72 },
];
