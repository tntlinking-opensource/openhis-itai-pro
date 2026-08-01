<script setup lang="ts">
import './section-title.css';

interface Props {
  title: string;
}

const props = defineProps<Props>();
</script>

<template>
  <div class="section-title font-semibold">
    {{ props.title }}
  </div>
</template>
