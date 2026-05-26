<template>
  <div class="border-t border-[var(--im-border)] bg-[var(--im-surface)] p-2.5">
    <div class="flex items-center gap-2">
      <button @click="triggerUpload('image')" :disabled="uploading"
        class="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-[var(--im-bg-secondary)] transition text-[var(--im-text-secondary)]"
        :title="$t('chat.uploadImage')">
        <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="m2.25 15.75 5.159-5.159a2.25 2.25 0 0 1 3.182 0l5.159 5.159m-1.5-1.5 1.409-1.409a2.25 2.25 0 0 1 3.182 0l2.909 2.909M3.75 21h16.5A2.25 2.25 0 0 0 22.5 18.75V5.25A2.25 2.25 0 0 0 20.25 3H3.75A2.25 2.25 0 0 0 1.5 5.25v13.5A2.25 2.25 0 0 0 3.75 21Z" />
        </svg>
      </button>
      <button @click="triggerUpload('file')" :disabled="uploading"
        class="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-[var(--im-bg-secondary)] transition text-[var(--im-text-secondary)]"
        :title="$t('chat.uploadFile')">
        <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 0 0-3.375-3.375h-1.5A1.125 1.125 0 0 1 13.5 7.125v-1.5a3.375 3.375 0 0 0-3.375-3.375H8.25m3.75 9v6m3-3H9m1.5-12H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 0 0-9-9Z" />
        </svg>
      </button>
      <input v-model="text" @keydown.enter="send" :placeholder="uploading ? $t('chat.uploading') : $t('chat.typeMessage')"
        class="im-input flex-1" :disabled="uploading" />
      <button @click="send" :disabled="!text.trim() || uploading"
        class="im-btn-primary text-xs px-4 py-2 disabled:opacity-30">
        {{ $t('chat.send') }}
      </button>
    </div>
    <input ref="imageInputRef" type="file" accept="image/*" class="hidden" @change="handleFileChange($event, 'image')" />
    <input ref="fileInputRef" type="file" class="hidden" @change="handleFileChange($event, 'file')" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { uploadFile } from '../../services/api'
import { ContentType } from '../../proto/constants'

const emit = defineEmits<{
  send: [text: string]
  sendFile: [msgType: number, url: string, fileName: string, fileSize: number]
}>()
const text = ref('')
const uploading = ref(false)
const imageInputRef = ref<HTMLInputElement>()
const fileInputRef = ref<HTMLInputElement>()

function send() {
  const trimmed = text.value.trim()
  if (!trimmed) return
  emit('send', trimmed)
  text.value = ''
}

function triggerUpload(type: 'image' | 'file') {
  if (type === 'image') imageInputRef.value?.click()
  else fileInputRef.value?.click()
}

async function handleFileChange(event: Event, type: 'image' | 'file') {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  // Reset input so same file can be re-selected
  input.value = ''

  // Validate image type
  if (type === 'image' && !file.type.startsWith('image/')) return

  uploading.value = true
  try {
    const res = await uploadFile(file)
    if (res.code === 0) {
      const msgType = type === 'image' ? ContentType.IMAGE : ContentType.FILE
      emit('sendFile', msgType, res.url, res.fileName, res.fileSize)
    }
  } catch {
    // Silently handle - the UI already shows disabled state
  } finally {
    uploading.value = false
  }
}
</script>
