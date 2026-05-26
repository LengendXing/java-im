<template>
  <div ref="container" class="flex-1 overflow-y-auto p-3 space-y-2.5">
    <div v-for="msg in chat.currentMessages" :key="msg.msgId"
      :class="['flex gap-2', msg.senderId === auth.userId ? 'flex-row-reverse' : 'flex-row']">
      <!-- Avatar -->
      <div class="im-avatar w-8 h-8 text-xs flex-shrink-0"
        :class="msg.senderId === auth.userId ? 'bg-[var(--im-green)] text-white' : 'bg-gray-500 text-white'">
        {{ msg.senderId === auth.userId ? (auth.nickname || '?').charAt(0).toUpperCase() : '🧑' }}
      </div>
      <!-- Bubble -->
      <div class="im-bubble" :class="msg.senderId === auth.userId ? 'im-bubble-self' : 'im-bubble-other'">
        <p>{{ msg.content?.text }}</p>
        <p class="text-[9px] mt-0.5" :class="msg.senderId === auth.userId ? 'text-green-800/60' : 'text-[var(--im-text-secondary)]'">
          {{ formatTime(msg.serverTime) }}
        </p>
      </div>
    </div>
    <div v-if="chat.currentMessages.length === 0" class="text-center text-[var(--im-text-secondary)] py-12 text-xs">
      {{ $t('chat.noMessages') }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { useChatStore } from '../../stores/chat'
import { useAuthStore } from '../../stores/auth'

const chat = useChatStore()
const auth = useAuthStore()
const container = ref<HTMLElement>()

watch(() => chat.currentMessages.length, async () => {
  await nextTick()
  container.value?.scrollTo({ top: container.value.scrollHeight, behavior: 'smooth' })
})

function formatTime(ts: number): string {
  if (!ts) return ''
  return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}
</script>
