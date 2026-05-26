<template>
  <div class="border-t border-[var(--im-border)] bg-[var(--im-surface)] p-2.5">
    <div class="flex items-center gap-2">
      <input v-model="text" @keydown.enter="send" :placeholder="$t('chat.typeMessage')"
        class="im-input flex-1" />
      <button @click="send" :disabled="!text.trim()"
        class="im-btn-primary text-xs px-4 py-2 disabled:opacity-30">
        {{ $t('chat.send') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const emit = defineEmits<{ send: [text: string] }>()
const text = ref('')

function send() {
  const trimmed = text.value.trim()
  if (!trimmed) return
  emit('send', trimmed)
  text.value = ''
}
</script>
