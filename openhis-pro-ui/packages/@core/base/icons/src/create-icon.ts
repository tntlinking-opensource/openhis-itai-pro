import { defineComponent, h } from 'vue';

import { Icon } from '@iconify/vue';

function createIconifyIcon(icon: string) {
  return defineComponent({
    name: `Icon-${icon}`,
    props: {
      size: {
        type: [Number, String],
        default: 16,
      },
    },
    setup(props, { attrs }) {
      return () =>
        h(Icon, {
          icon,
          style: {
            width: typeof props.size === 'number' ? `${props.size}px` : props.size,
            height: typeof props.size === 'number' ? `${props.size}px` : props.size,
          },
          ...attrs,
        });
    },
  });
}

export { createIconifyIcon };
