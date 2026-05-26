<template>
  <div ref="container" class="flex-1 overflow-y-auto p-3 space-y-2.5">
    <div v-for="msg in chat.currentMessages" :key="msg.msgId"
      :class="['flex gap-2', msg.senderId === auth.userId ? 'flex-row-reverse' : 'flex-row']"
      @contextmenu.prevent="showContextMenu($event, msg)">
      <!-- Avatar -->
      <div class="im-avatar w-8 h-8 text-xs flex-shrink-0"
        :class="msg.senderId === auth.userId ? 'bg-[var(--im-green)] text-white' : 'bg-gray-500 text-white'">
        {{ msg.senderId === auth.userId ? (auth.nickname || '?').charAt(0).toUpperCase() : '🧑' }}
      </div>
      <!-- Bubble -->
      <div class="im-bubble" :class="msg.senderId === auth.userId ? 'im-bubble-self' : 'im-bubble-other'">
        <!-- Recalled message -->
        <template v-if="msg.content?.msgType === 99">
          <p class="italic text-[var(--im-text-secondary)] text-xs">{{ $t('chat.recalled') }}</p>
        </template>
        <!-- Image message -->
        <template v-else-if="msg.content?.msgType === 2 && msg.content?.url">
          <img :src="msg.content.url" :alt="msg.content?.fileName || 'Image'"
            class="max-w-[240px] max-h-[240px] rounded-lg cursor-pointer object-cover"
            @click="previewImage(msg.content!.url)" />
        </template>
        <!-- File message -->
        <template v-else-if="msg.content?.msgType === 3 && msg.content?.url">
          <div class="flex items-center gap-2 min-w-[160px]">
            <svg xmlns="http://www.w3.org/2000/svg" class="w-8 h-8 text-[var(--im-green)] flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 0 0-3.375-3.375h-1.5A1.125 1.125 0 0 1 13.5 7.125v-1.5a3.375 3.375 0 0 0-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 0 0-9-9Z" />
            </svg>
            <div class="min-w-0">
              <p class="text-sm truncate">{{ msg.content?.fileName || 'File' }}</p>
              <p class="text-[10px] text-[var(--im-text-secondary)]">{{ formatFileSize(msg.content?.fileSize || 0) }}</p>
            </div>
          </div>
        </template>
        <!-- Text message -->
        <template v-else>
          <p>{{ msg.content?.text }}</p>
        </template>
        <p class="text-[9px] mt-0.5" :class="msg.senderId === auth.userId ? 'text-green-800/60' : 'text-[var(--im-text-secondary)]'">
          {{ formatTime(msg.serverTime) }}
        </p>
      </div>
    </div>
    <div v-if="chat.currentMessages.length === 0" class="text-center text-[var(--im-text-secondary)] py-12 text-xs">
      {{ $t('chat.noMessages') }}
    </div>
    <!-- Context Menu -->
    <div v-if="contextMenu.visible" ref="contextMenuRef"
      class="fixed bg-[var(--im-surface)] border border-[var(--im-border)] rounded-lg shadow-lg py-1 z-50 min-w-[100px]"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }">
      <button v-if="canRecall" @click="recallMsg"
        class="w-full text-left px-3 py-1.5 text-sm hover:bg-[var(--im-bg-secondary)] transition">
        {{ $t('chat.recall') }}
      </button>
    </div>
    <!-- Image Preview Overlay -->
    <div v-if="previewUrl" class="fixed inset-0 bg-black/80 flex items-center justify-center z-50" @click="previewUrl = ''">
      <img :src="previewUrl" class="max-w-[90vw] max-h-[90vh] object-contain" @click.stop />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useChatStore } from '../../stores/chat'
import { useAuthStore } from '../../stores/auth'
import type { Message } from '../../proto/im'

const chat = useChatStore()
const auth = useAuthStore()
const container = ref<HTMLElement>()
const contextMenuRef = ref<HTMLElement>()
const previewUrl = ref('')

const contextMenu = ref({ visible: false, x: 0, y: 0, msg: null as Message | null })

const canRecall = ref(false)

watch(() => chat.currentMessages.length, async () => {
  await nextTick()
  container.value?.scrollTo({ top: container.value.scrollHeight, behavior: 'smooth' })
})

function formatTime(ts: number): string {
  if (!ts) return ''
  return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function previewImage(url: string) {
  previewUrl.value = url
}

function showContextMenu(event: MouseEvent, msg: Message) {
  // Only show recall for own messages within 2 minutes
  const isOwn = msg.senderId === auth.userId
  const within2Min = !!msg.serverTime && (Date.now() - msg.serverTime < 2 * 60 * 1000)
  canRecall.value = isOwn && within2Min && msg.content?.msgType !== 99

  if (!canRecall.value) return

  contextMenu.value = { visible: true, x: event.clientX, y: event.clientY, msg }
}

async function recallMsg() {
  const msg = contextMenu.value.msg
  contextMenu.value.visible = false
  if (!msg) return
  await chat.recallMessage(msg.msgId, chat.currentSessionId)
}

function handleClickOutside(e: MouseEvent) {
  if (contextMenu.value.visible && contextMenuRef.value && !contextMenuRef.value.contains(e.target as Node)) {
    contextMenu.value.visible = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>
