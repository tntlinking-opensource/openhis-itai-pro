/**
 * 药品/耗材/诊疗目录 Excel 导出列清单。
 *
 * <p>与后端导入模板表头对齐：首列「编号」+ ImportConstants.COLUMN_HEADERS。
 * 后端按中文表头（field 或 title）匹配 @Excel 列，勿改成页面表格子集。</p>
 */
import type { ExcelExportColumnPayload } from '#/utils/export/build-excel-export-columns';

/** 导出首列：业务编号（与 *CatalogImportConstants.EXPORT_BUS_NO_HEADER 一致） */
const EXPORT_BUS_NO_HEADER = '编号';

function toExportColumns(headers: string[]): ExcelExportColumnPayload[] {
  return headers.map((header) => ({ field: header, title: header }));
}

/**
 * 药品目录导出列（MedicationCatalogImportConstants：编号 + COLUMN_HEADERS）
 */
export const MEDICATION_CATALOG_EXPORT_COLUMNS: ExcelExportColumnPayload[] = toExportColumns([
  EXPORT_BUS_NO_HEADER,
  // A 基础信息（12）
  '药品名称',
  '商品名',
  '药品分类',
  '生产厂家',
  '财务类型',
  '批准文号',
  '贯标国家编码',
  '医保编码',
  '医保等级',
  '病案结算类型',
  '医保对码',
  '医保类别',
  // B 临床与规格（18）
  '药品性质',
  '规格',
  '剂量单位',
  '剂量/拆零倍数',
  '剂型',
  '皮试判别',
  '输液标记',
  '用量限定',
  '默认用法',
  '默认频次',
  '单次剂量',
  '单次最小剂量',
  '单次最大剂量',
  '用法说明',
  '限制使用范围',
  '药品说明',
  'DDD值',
  'DDD单位',
  // C 库存与单位（16）
  '采购单位',
  '拆零单位',
  '采购/拆零倍数',
  '包装单位（定价基准单位）',
  '包装/拆零倍数',
  '追溯码单位',
  '追溯码/拆零倍数',
  '库存单位',
  '库存/拆零倍数',
  '购入价（包装单位）',
  '零售价（包装单位）',
  '最高零售价（包装单位）',
  '门诊拆分属性',
  '住院临时医嘱拆分属性',
  '警戒线最低值（库存单位）',
  '警戒线最高值（库存单位）',
  // D 业务信息（10）
  '院内旧编码',
  '69码',
  '基药标识',
  '儿童用药标志',
  '自制药标识',
  '限制使用',
  '抗生素',
  '处方药标志',
  '国家重点监控药品',
  '集采药品',
]);

/**
 * 耗材目录导出列（DeviceCatalogImportConstants：编号 + COLUMN_HEADERS）
 */
export const DEVICE_CATALOG_EXPORT_COLUMNS: ExcelExportColumnPayload[] = toExportColumns([
  EXPORT_BUS_NO_HEADER,
  // A 基础信息（13）
  '耗材名称',
  '商品名',
  '耗材分类',
  '耗材种类',
  '生产厂家',
  '财务类型',
  '包装规格',
  '批准文号',
  '医保编码',
  '医保等级',
  '病案结算类型',
  '医保对码',
  '医保类别',
  // B 库存与单位（12）
  '采购单位',
  '拆零单位',
  '采购/拆零倍数',
  '包装单位（定价基准单位）',
  '包装/拆零倍数',
  '库存单位',
  '库存/拆零倍数',
  '购入价（包装单位）',
  '零售价（包装单位）',
  '最高零售价（包装单位）',
  '警戒线最低值（库存单位）',
  '警戒线最高值（库存单位）',
  // C 业务信息（7）
  '院内旧编码',
  '型号',
  '过敏标记',
  '医保标记',
  '高值耗材标志',
  '收费耗材',
  '说明',
]);

/**
 * 诊疗目录导出列（ServiceCatalogImportConstants：编号 + COLUMN_HEADERS）
 */
export const SERVICE_CATALOG_EXPORT_COLUMNS: ExcelExportColumnPayload[] = toExportColumns([
  EXPORT_BUS_NO_HEADER,
  // A 基础信息（5）
  '诊疗名称',
  '目录分类',
  '业务类型',
  '销售/计价单位',
  '财务类型',
  // B 计价（3）
  '成本价',
  '零售价',
  '最高零售价',
  // C 医保与说明（8）
  '医保标志',
  '医保编号',
  '医保类别',
  '医保对码标记',
  '医保等级',
  '病案结算类型',
  '院内旧编码',
  '描述',
]);
