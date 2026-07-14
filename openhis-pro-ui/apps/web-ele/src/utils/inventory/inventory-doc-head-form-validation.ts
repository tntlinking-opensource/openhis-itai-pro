import type { FormInstance, FormItemRule, FormRules } from 'element-plus';

import { isSpecificInventoryLocationId } from '#/utils/inventory/inventory-workbench-list';

/** 单头库位必选：Element Plus 原生校验（表单项变红，不用 ElMessage） */
export function inventoryLocationRequiredRule(label = '库位'): FormItemRule {
  return {
    required: true,
    validator: (_rule, value, callback) => {
      if (!isSpecificInventoryLocationId(value)) {
        callback(new Error(`请选择${label}`));
        return;
      }
      callback();
    },
    trigger: 'change',
  };
}

/** 单头下拉必选（供应商等）：Element Plus 原生校验 */
export function inventoryRequiredSelectRule(label: string): FormItemRule {
  return {
    required: true,
    validator: (_rule, value, callback) => {
      if (value === undefined || value === null || String(value).trim() === '') {
        callback(new Error(`请选择${label}`));
        return;
      }
      callback();
    },
    trigger: 'change',
  };
}

/** 批量构建单头库位 rules（prop → 展示标签） */
export function buildInventoryLocationHeadFormRules(fields: Record<string, string>): FormRules {
  const rules: FormRules = {};
  for (const [prop, label] of Object.entries(fields)) {
    rules[prop] = [inventoryLocationRequiredRule(label)];
  }
  return rules;
}

/** 触发指定字段校验；任一失败返回 false（不弹 Message） */
export async function validateInventoryHeadFormFields(
  formRef: FormInstance | undefined,
  props: string | string[],
): Promise<boolean> {
  if (!formRef) return false;
  const fields = Array.isArray(props) ? props : [props];
  let ok = true;
  for (const prop of fields) {
    try {
      await formRef.validateField(prop);
    } catch {
      ok = false;
    }
  }
  return ok;
}

/** 清除单头表单校验态（新建/重置时调用） */
export function clearInventoryHeadFormValidation(formRef: FormInstance | undefined): void {
  formRef?.clearValidate();
}
