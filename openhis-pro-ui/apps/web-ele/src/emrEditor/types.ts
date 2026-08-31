/**
 * 可绑定患者/就诊等系统字段的元数据项（后续可由配置中心下发）。
 * - fieldKey：模板与病历中的机器字段名，用于从患者信息取值
 * - editable：false 时正文内不可改，且值随 metadataValues 自动刷新；true 时可手工改写展示内容
 */
export interface EmrMetadataField {
  fieldKey: string;
  label: string;
  /** 默认 true：可编辑；false 表示仅展示绑定值（随患者信息更新） */
  editable?: boolean;
  /** 数据类型（date/datetime/string…），决定拖入编辑器时的控件形态 */
  dataType?: string;
  /** 取值来源（与 MetadataValueSource 对齐），侧栏按此分组 */
  valueSource?: string;
}
