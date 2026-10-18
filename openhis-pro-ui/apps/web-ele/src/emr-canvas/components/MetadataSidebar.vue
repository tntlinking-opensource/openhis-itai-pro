<script setup lang="ts">
import { computed, ref } from 'vue';

import { ElButton, ElInput } from 'element-plus';

import { EMR_METADATA_FIELDS, type EmrMetadataField } from '#/emr-canvas/constants/emr-metadata';
/** 外挂样式：院点 override 生产包 scoped 会丢，元数据芯片靠相对路径 css */
import './metadata-sidebar.css';

defineOptions({ name: 'MetadataSidebar' });

interface Props {
  canApply?: boolean;
  showApply?: boolean;
}

withDefaults(defineProps<Props>(), {
  canApply: false,
  showApply: true,
});

const emit = defineEmits<{
  insert: [field: EmrMetadataField];
  apply: [];
}>();

const keyword = ref('');
const openGroups = ref<Record<string, boolean>>({
  患者: true,
  就诊: true,
  医护: true,
});

const groups = computed(() => {
  const q = keyword.value.trim();
  const list = EMR_METADATA_FIELDS.filter((item) => {
    if (!q) return true;
    return item.label.includes(q) || item.fieldKey.toLowerCase().includes(q.toLowerCase());
  });
  const order: EmrMetadataField['group'][] = ['患者', '就诊', '医护'];
  return order
    .map((group) => ({
      group,
      fields: list.filter((item) => item.group === group),
    }))
    .filter((item) => item.fields.length);
});

/** 展开或收起元数据分组 */
function toggle(group: string) {
  openGroups.value[group] = !openGroups.value[group];
}
</script>

<template>
  <aside class="meta-side" aria-label="元数据字段">
    <header class="meta-side__head">
      <div class="meta-side__title">元数据</div>
      <p class="meta-side__hint">点击字段插入到光标处</p>
    </header>
    <ElInput v-model="keyword" size="small" clearable placeholder="搜索名称 / 编码" />
    <div class="meta-side__list">
      <div v-if="!groups.length" class="meta-side__empty">无匹配字段</div>
      <section v-for="g in groups" :key="g.group" class="meta-group">
        <button type="button" class="meta-group__head" @click="toggle(g.group)">
          <span class="meta-group__caret" :class="{ 'is-open': openGroups[g.group] }">▾</span>
          <span class="meta-group__label">{{ g.group }}</span>
          <span class="meta-group__count">{{ g.fields.length }}</span>
        </button>
        <div v-show="openGroups[g.group]" class="meta-group__body">
          <button
            v-for="f in g.fields"
            :key="f.fieldKey"
            type="button"
            class="meta-chip"
            :title="`${f.label}（${f.fieldKey}）`"
            @click="emit('insert', f)"
          >
            <span class="meta-chip__dot" :class="f.editable ? 'is-edit' : 'is-lock'" />
            <span class="meta-chip__label">{{ f.label }}</span>
            <span class="meta-chip__badge" :class="f.editable ? 'is-edit' : 'is-lock'">
              {{ f.editable ? '可改' : '只读' }}
            </span>
          </button>
        </div>
      </section>
    </div>
    <ElButton
      v-if="showApply"
      class="meta-side__apply"
      type="primary"
      plain
      size="small"
      :disabled="!canApply"
      @click="emit('apply')"
    >
      带入当前患者
    </ElButton>
  </aside>
</template>
