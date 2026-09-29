/**
 * 产品内置诊疗项目（按 name 精确匹配；对齐后端 BuiltInActivityDefinitionConstants）。
 *
 * <p>缺则皮试/嘱托/代煎/合管采血费等链路失败；维护台禁止删、停用、改名、改类型/类目。</p>
 */

/** 皮试检查（开皮试药 companion） */
export const BUILT_IN_ACTIVITY_NAME_SKIN_TEST = '皮试检查';

/** 嘱托（住院嘱托医嘱） */
export const BUILT_IN_ACTIVITY_NAME_ENTRUST = '嘱托';

/** 代煎中药 */
export const BUILT_IN_ACTIVITY_NAME_DECOCTION = '代煎中药';

/** 采血费（标本合管勾选） */
export const BUILT_IN_ACTIVITY_NAME_BLOOD_COLLECTION = '采血费';

/** 内置名称集合 */
export const BUILT_IN_ACTIVITY_NAMES = new Set<string>([
  BUILT_IN_ACTIVITY_NAME_SKIN_TEST,
  BUILT_IN_ACTIVITY_NAME_ENTRUST,
  BUILT_IN_ACTIVITY_NAME_DECOCTION,
  BUILT_IN_ACTIVITY_NAME_BLOOD_COLLECTION,
]);

/** 内置项锁定的表单字段（名称/类目/类型） */
export const BUILT_IN_ACTIVITY_LOCKED_FORM_FIELDS = ['name', 'categoryCode', 'typeEnum'] as const;

/** 是否为产品内置诊疗项目名称 */
export function isBuiltInActivityName(name: string | null | undefined): boolean {
  const trimmed = String(name ?? '').trim();
  if (!trimmed) {
    return false;
  }
  return BUILT_IN_ACTIVITY_NAMES.has(trimmed);
}

/** 内置项维护提示文案 */
export const BUILT_IN_ACTIVITY_EDIT_HINT = '系统内置诊疗项目：禁止删除、停用、改名、修改类型与类目';
