import { defineComponent, h } from 'vue';

import { Field, Form, defineRule } from 'vee-validate';
import { mount, flushPromises } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

defineRule('required', (value: unknown) => {
  if (value === undefined || value === null || value === '') {
    return '必填';
  }
  return true;
});

const RequiredField = defineComponent({
  props: {
    validateOnBlur: { type: Boolean, default: true },
  },
  setup(props) {
    return () =>
      h(
        Form,
        {},
        {
          default: () =>
            h(
              Field,
              {
                name: 'name',
                rules: 'required',
                validateOnBlur: props.validateOnBlur,
                validateOnChange: false,
                validateOnInput: false,
                validateOnModelUpdate: false,
              },
              {
                default: (slotProps: {
                  field: { name: string; value: string; onBlur: (e: Event) => void };
                  errors: string[];
                }) =>
                  h('div', { class: 'field-wrap' }, [
                    h('input', {
                      name: slotProps.field.name,
                      value: slotProps.field.value,
                      onBlur: slotProps.field.onBlur,
                    }),
                    slotProps.errors[0]
                      ? h('span', { class: 'field-error' }, slotProps.errors[0])
                      : null,
                  ]),
              },
            ),
        },
      );
  },
});

/** 与 medication-catalog useVbenForm commonConfig.formFieldProps 行为一致 */
describe('仅保存时校验（vee-validate validateOnBlur）', () => {
  it('validateOnBlur=true 时失焦显示错误', async () => {
    const wrapper = mount(RequiredField, {
      props: { validateOnBlur: true },
      attachTo: document.body,
    });

    await wrapper.find('input').trigger('focus');
    await wrapper.find('input').trigger('blur');
    await flushPromises();

    expect(wrapper.find('.field-error').exists()).toBe(true);
    wrapper.unmount();
  });

  it('validateOnBlur=false 时失焦不显示错误', async () => {
    const wrapper = mount(RequiredField, {
      props: { validateOnBlur: false },
      attachTo: document.body,
    });

    await wrapper.find('input').trigger('focus');
    await wrapper.find('input').trigger('blur');
    await flushPromises();

    expect(wrapper.find('.field-error').exists()).toBe(false);
    wrapper.unmount();
  });
});
