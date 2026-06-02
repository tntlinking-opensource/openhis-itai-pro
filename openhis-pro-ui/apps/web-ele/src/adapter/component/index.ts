/**
 * 通用组件共同的使用的基础组件，原先放在 adapter/form 内部，限制了使用范围，这里提取出来，方便其他地方使用
 * 可用于 vben-form、vben-modal、vben-drawer 等组件使用,
 */
/* eslint-disable vue/one-component-per-file -- 本文件为组件注册表，需在单文件中导出多个包装组件 */

import type { Component, CSSProperties } from 'vue';

import type { BaseFormComponentType } from '@vben/common-ui';
import type { Recordable } from '@vben/types';

import { defineComponent, h, nextTick, ref } from 'vue';

import { ApiComponent, globalShareState, IconPicker } from '@vben/common-ui';
import { $t } from '@vben/locales';

import CheckboxInlineGroup from '#/components/checkbox-inline-group.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import SectionDivider from '#/components/section-divider.vue';
import SelectDepartment from '#/components/select-department.vue';
import SelectWithBeforeSelect from '#/components/select-with-before-select.vue';
import {
  ElButton,
  ElCheckbox,
  ElCheckboxButton,
  ElCheckboxGroup,
  ElDatePicker,
  ElDivider,
  ElInput,
  ElInputNumber,
  ElNotification,
  ElRadio,
  ElRadioButton,
  ElRadioGroup,
  ElSelectV2,
  ElSpace,
  ElSwitch,
  ElTimePicker,
  ElTreeSelect,
  ElUpload,
} from 'element-plus';

/**
 * Element Plus + 表单内 Vue 封装同步注册（对齐 Vben 文档默认适配写法），避免 useVbenForm 逐项异步挂载造成卡顿；
 * `unplugin-element-plus` 会按需补齐样式。
 */

const withDefaultPlaceholder = <T extends Component>(
  component: T,
  type: 'input' | 'select',
  componentProps: Recordable<any> = {},
) => {
  return defineComponent({
    name: component.name,
    inheritAttrs: false,
    setup: (props: any, { attrs, expose, slots }) => {
      const placeholder = props?.placeholder || attrs?.placeholder || $t(`ui.placeholder.${type}`);
      // 透传组件暴露的方法
      const innerRef = ref();
      expose(
        new Proxy(
          {},
          {
            get: (_target, key) => innerRef.value?.[key],
            has: (_target, key) => key in (innerRef.value || {}),
          },
        ),
      );
      return () =>
        h(component, { ...componentProps, placeholder, ...props, ...attrs, ref: innerRef }, slots);
    },
  });
};

// 这里需要自行根据业务组件库进行适配，需要用到的组件都需要在这里类型说明
export type ComponentType =
  | 'ApiSelect'
  | 'ApiTreeSelect'
  | 'Checkbox'
  | 'CheckboxGroup'
  | 'CheckboxInlineGroup'
  | 'DatePicker'
  | 'Divider'
  | 'IconPicker'
  | 'Input'
  | 'InputArea'
  | 'InputNumber'
  | 'RadioGroup'
  | 'SectionDivider'
  | 'SectionTitle'
  | 'Select'
  | 'SelectDepartment'
  | 'Space'
  | 'Switch'
  | 'TimePicker'
  | 'TreeSelect'
  | 'Upload'
  | BaseFormComponentType;

function initComponentAdapter() {
  const components: Partial<Record<ComponentType, Component>> = {
    // 如果你的组件体积比较大，可以使用异步加载
    // Button: () =>
    // import('xxx').then((res) => res.Button),
    ApiSelect: withDefaultPlaceholder(
      {
        ...ApiComponent,
        name: 'ApiSelect',
      },
      'select',
      {
        component: ElSelectV2,
        loadingSlot: 'loading',
        visibleEvent: 'onVisibleChange',
      },
    ),
    ApiTreeSelect: withDefaultPlaceholder(
      {
        ...ApiComponent,
        name: 'ApiTreeSelect',
      },
      'select',
      {
        component: ElTreeSelect,
        props: { label: 'label', children: 'children' },
        nodeKey: 'value',
        loadingSlot: 'loading',
        optionsPropName: 'data',
        visibleEvent: 'onVisibleChange',
      },
    ),
    Checkbox: defineComponent({
      name: 'CheckboxAdapter',
      inheritAttrs: false,
      setup(props: any, { attrs, expose, slots }) {
        const innerRef = ref();
        expose(
          new Proxy(
            {},
            {
              get: (_target, key) => innerRef.value?.[key],
              has: (_target, key) => key in (innerRef.value || {}),
            },
          ),
        );
        return () => {
          // 将 0/1 转换为 true/false 传递给 ElCheckbox
          const modelValue = props.modelValue ?? attrs.modelValue;
          const checkboxValue = modelValue === 1 || modelValue === '1';

          // 处理更新事件，将 true/false 转换为 0/1
          const handleUpdate = (value: boolean) => {
            const newValue = value ? 1 : 0;
            const propHandler = props['onUpdate:modelValue'];
            const attrHandler = attrs['onUpdate:modelValue'];

            if (typeof propHandler === 'function') {
              propHandler(newValue);
            }
            if (typeof attrHandler === 'function') {
              attrHandler(newValue);
            } else if (Array.isArray(attrHandler)) {
              attrHandler.forEach((handler: any) => {
                if (typeof handler === 'function') {
                  handler(newValue);
                }
              });
            }
          };

          return h(
            ElCheckbox,
            {
              ...props,
              ...attrs,
              modelValue: checkboxValue,
              'onUpdate:modelValue': handleUpdate,
              ref: innerRef,
            },
            slots,
          );
        };
      },
    }),
    CheckboxGroup: (props, { attrs, slots }) => {
      let defaultSlot;
      if (Reflect.has(slots, 'default')) {
        defaultSlot = slots.default;
      } else {
        const { options, isButton } = attrs;
        if (Array.isArray(options)) {
          defaultSlot = () =>
            options.map((option) => h(isButton ? ElCheckboxButton : ElCheckbox, option));
        }
      }
      return h(ElCheckboxGroup, { ...props, ...attrs }, { ...slots, default: defaultSlot });
    },
    // 自定义默认按钮
    DefaultButton: (props, { attrs, slots }) => {
      return h(ElButton, { ...props, attrs, type: 'info' }, slots);
    },
    // 自定义主要按钮
    PrimaryButton: (props, { attrs, slots }) => {
      return h(ElButton, { ...props, attrs, type: 'primary' }, slots);
    },
    Divider: ElDivider,
    SectionDivider: defineComponent({
      name: 'SectionDividerAdapter',
      setup(props: any, { attrs }) {
        return () =>
          h(SectionDivider, {
            ...props,
            ...attrs,
            title: attrs.title || attrs.label || props.title || props.label,
          });
      },
    }),
    /** 表单分区标题：左侧主色竖条 + 标题（layouts/section-title.vue） */
    SectionTitle: defineComponent({
      name: 'SectionTitleAdapter',
      setup(props: any, { attrs }) {
        return () =>
          h(SectionTitle, {
            title: String(attrs.title ?? attrs.label ?? props.title ?? props.label ?? ''),
          });
      },
    }),
    CheckboxInlineGroup: defineComponent({
      name: 'CheckboxInlineGroupAdapter',
      setup(props: any, { attrs }) {
        return () =>
          h(CheckboxInlineGroup, {
            ...props,
            ...attrs,
            fields: attrs.fields || props.fields,
          });
      },
    }),
    IconPicker: withDefaultPlaceholder(IconPicker, 'select', {
      iconSlot: 'append',
      modelValueProp: 'model-value',
      inputComponent: ElInput,
    }),
    Input: defineComponent({
      name: 'InputAdapter',
      inheritAttrs: false,
      setup(props: any, { attrs, expose, slots }) {
        const placeholder = props?.placeholder || attrs?.placeholder || $t('ui.placeholder.input');
        const innerRef = ref();
        expose(
          new Proxy(
            {},
            {
              get: (_target, key) => innerRef.value?.[key],
              has: (_target, key) => key in (innerRef.value || {}),
            },
          ),
        );
        return () => {
          const suffixText = props?.suffixText || attrs?.suffixText;
          const inputSlots: any = { ...slots };
          if (suffixText) {
            inputSlots.suffix = () => suffixText;
          }
          return h(
            ElInput,
            {
              placeholder,
              ...props,
              ...attrs,
              ref: innerRef,
              style: { width: '100%', ...(props.style || attrs.style) },
            },
            inputSlots,
          );
        };
      },
    }),
    InputArea: defineComponent({
      name: 'InputArea',
      inheritAttrs: false,
      setup(props: any, { attrs }) {
        return () => {
          const finalProps = {
            ...props,
            ...attrs,
            type: 'textarea',
            style: { width: '100%', ...(attrs.style as CSSProperties) },
          };
          return h(ElInput, finalProps);
        };
      },
    }),
    InputNumber: withDefaultPlaceholder(ElInputNumber, 'input', {
      controlsPosition: 'right',
    }),
    RadioGroup: (props, { attrs, slots }) => {
      let defaultSlot;
      if (Reflect.has(slots, 'default')) {
        defaultSlot = slots.default;
      } else {
        const { options } = attrs;
        if (Array.isArray(options)) {
          defaultSlot = () =>
            options.map((option) => h(attrs.isButton ? ElRadioButton : ElRadio, option));
        }
      }
      return h(ElRadioGroup, { ...props, ...attrs }, { ...slots, default: defaultSlot });
    },
    Select: defineComponent({
      name: 'SelectAdapter',
      inheritAttrs: false,
      setup(props: any, { attrs, slots }) {
        return () => h(SelectWithBeforeSelect, { ...props, ...attrs }, slots);
      },
    }),
    SelectDepartment: defineComponent({
      name: 'SelectDepartmentAdapter',
      inheritAttrs: false,
      setup(props: any, { attrs }) {
        return () =>
          h(SelectDepartment, {
            formMode: true,
            clearable: attrs.clearable ?? props?.clearable ?? true,
            ...props,
            ...attrs,
          });
      },
    }),
    Space: ElSpace,
    Switch: ElSwitch,
    TimePicker: (props, { attrs, slots }) => {
      const { name, id, isRange } = props;
      const extraProps: Recordable<any> = {};
      if (isRange) {
        if (name && !Array.isArray(name)) {
          extraProps.name = [name, `${name}_end`];
        }
        if (id && !Array.isArray(id)) {
          extraProps.id = [id, `${id}_end`];
        }
      }
      return h(
        ElTimePicker,
        {
          ...props,
          ...attrs,
          ...extraProps,
        },
        slots,
      );
    },
    DatePicker: (props, { attrs, slots }) => {
      const { name, id, type } = props;
      const extraProps: Recordable<any> = {};
      if (type && type.includes('range')) {
        if (name && !Array.isArray(name)) {
          extraProps.name = [name, `${name}_end`];
        }
        if (id && !Array.isArray(id)) {
          extraProps.id = [id, `${id}_end`];
        }
      }
      return h(
        ElDatePicker,
        {
          ...props,
          ...attrs,
          ...extraProps,
        },
        slots,
      );
    },
    TreeSelect: withDefaultPlaceholder(ElTreeSelect, 'select'),
    Upload: ElUpload,
  };

  // 将组件注册到全局共享状态中
  globalShareState.setComponents(components);

  // 定义全局共享状态中的消息提示
  globalShareState.defineMessage({
    // 复制成功消息提示
    copyPreferencesSuccess: (title, content) => {
      ElNotification({
        title,
        message: content,
        position: 'bottom-right',
        duration: 0,
        type: 'success',
      });
    },
  });

  // 全局初始化 Select 选项 tooltip 支持
  if (typeof document !== 'undefined') {
    // 为选项添加 title 的函数
    const addTitleToOptions = () => {
      // 查找所有 SelectV2 的选项容器（下拉框）
      const poppers = document.querySelectorAll('.el-select-v2__popper, .el-popper');
      poppers.forEach((popper) => {
        // 查找所有选项
        const options = popper.querySelectorAll('.el-select-v2__option');
        options.forEach((option) => {
          // 如果已经有 title，跳过
          if (option.getAttribute('title')) return;

          // 查找选项文本元素
          const textElement =
            option.querySelector('.el-select-v2__option-item') ||
            option.querySelector('span') ||
            option;
          const text = textElement.textContent?.trim() || '';
          if (text) {
            // 直接设置 title 属性到选项元素上
            option.setAttribute('title', text);
            // 同时也设置到文本元素上，确保能显示
            if (textElement !== option) {
              textElement.setAttribute('title', text);
            }
          }
        });
      });
    };

    // 使用事件委托，监听下拉框的显示
    const handleDropdownShow = () => {
      nextTick(() => {
        addTitleToOptions();
      });
    };

    // 监听点击事件，当点击 Select 时触发
    const clickHandler = (e: Event) => {
      const target = e.target as HTMLElement;
      if (target.closest('.el-select-v2') || target.closest('.el-select')) {
        // 延迟执行，确保下拉框已经渲染
        setTimeout(() => {
          handleDropdownShow();
        }, 150);
      }
    };
    document.addEventListener('click', clickHandler, true);

    // 监听鼠标进入事件，当鼠标进入 Select 区域时也触发
    const mouseEnterHandler = (e: Event) => {
      const target = e.target;
      if (
        target &&
        target instanceof HTMLElement &&
        target.closest &&
        target.closest('.el-select-v2')
      ) {
        setTimeout(() => {
          handleDropdownShow();
        }, 100);
      }
    };
    document.addEventListener('mouseenter', mouseEnterHandler, true);

    // 使用 MutationObserver 监听下拉框的显示
    const observer = new MutationObserver((mutations) => {
      let shouldUpdate = false;
      mutations.forEach((mutation) => {
        mutation.addedNodes.forEach((node) => {
          if (node.nodeType === 1) {
            const element = node as HTMLElement;
            // 检查是否是下拉框或选项
            if (
              element.classList?.contains('el-select-v2__popper') ||
              element.classList?.contains('el-popper') ||
              element.querySelector?.('.el-select-v2__option')
            ) {
              shouldUpdate = true;
            }
          }
        });
      });
      if (shouldUpdate) {
        handleDropdownShow();
      }
    });

    observer.observe(document.body, {
      childList: true,
      subtree: true,
    });

    // 定期检查（作为备用方案）
    const intervalId = setInterval(() => {
      addTitleToOptions();
    }, 500);

    // 页面卸载时清理
    if (typeof window !== 'undefined') {
      window.addEventListener('beforeunload', () => {
        clearInterval(intervalId);
        document.removeEventListener('click', clickHandler, true);
        document.removeEventListener('mouseenter', mouseEnterHandler, true);
        observer.disconnect();
      });
    }
  }
}

export { initComponentAdapter };
