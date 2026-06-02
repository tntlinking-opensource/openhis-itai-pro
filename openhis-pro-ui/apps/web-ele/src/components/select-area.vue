<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';

import { ElCascader } from 'element-plus';
import rawRegionData from 'province-city-china/dist/level.json';

interface LevelItem {
  code: string;
  name: string;
  children?: LevelItem[];
}

interface RegionOption {
  value: string;
  label: string;
  children?: RegionOption[];
}

interface Props {
  modelValue?: string[]; // ['110000','110100','110101'] 省/市/区 code
  placeholder?: string;
  disabled?: boolean;
  clearable?: boolean;
  size?: 'default' | 'large' | 'small';
  style?: Record<string, any> | string;
  // 最大层级，默认 3：省/市/区；只要省市就传 2
  maxLevel?: 2 | 3 | 4;
}

const props = withDefaults(defineProps<Props>(), {
  style: undefined,
  modelValue: () => [],
  placeholder: '请选择省市区',
  disabled: false,
  clearable: true,
  size: 'default',
  maxLevel: 3,
});

const emit = defineEmits<{
  change: [value: string[]];
  'update:modelValue': [value: string[]];
}>();

const regionData = ref<RegionOption[]>([]);
const selectedValue = ref<string[]>(props.modelValue || []);

watch(
  () => props.modelValue,
  (val) => {
    selectedValue.value = val || [];
  },
  { immediate: true },
);

function toOptions(list: LevelItem[], level: number, maxLevel: number): RegionOption[] {
  return list.map((item) => {
    const node: RegionOption = {
      value: item.code,
      label: item.name,
    };
    if (item.children && item.children.length > 0 && level < maxLevel) {
      node.children = toOptions(item.children, level + 1, maxLevel);
    }
    return node;
  });
}

const handleChange = (val: unknown) => {
  const arr = (val || []) as string[];
  selectedValue.value = arr;
  emit('update:modelValue', arr);
  emit('change', arr);
};

onMounted(() => {
  const list = (rawRegionData || []) as LevelItem[];
  regionData.value = toOptions(list, 1, props.maxLevel);
});
</script>

<template>
  <ElCascader
    v-model="selectedValue"
    :options="regionData"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :size="size"
    :style="style"
    filterable
    :props="{
      value: 'value',
      label: 'label',
      children: 'children',
    }"
    @change="handleChange"
  />
</template>
