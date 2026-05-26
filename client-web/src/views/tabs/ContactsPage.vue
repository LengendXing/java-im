<template>
  <div class="h-full flex flex-col bg-[var(--im-surface)]">
    <div class="p-2">
      <div class="relative">
        <input v-model="search" :placeholder="$t('contacts.search')" class="im-input bg-[var(--im-bg-secondary)] pl-8" />
        <span class="absolute left-2.5 top-2.5 text-[var(--im-text-secondary)] text-xs">🔍</span>
      </div>
    </div>
    <!-- Quick actions -->
    <div class="px-4 py-2 border-b border-[var(--im-border)] space-y-1">
      <div class="flex items-center gap-3 py-2 cursor-pointer hover:bg-[var(--im-bg-secondary)] rounded-lg px-2" @click="showAddFriend = true">
        <span class="text-lg">🟡</span>
        <span class="text-sm">{{ $t('contacts.newFriend') }}</span>
      </div>
      <div class="flex items-center gap-3 py-2 cursor-pointer hover:bg-[var(--im-bg-secondary)] rounded-lg px-2" @click="showCreateGroup = true">
        <span class="text-lg">🟢</span>
        <span class="text-sm">{{ $t('contacts.groupChats') }}</span>
      </div>
    </div>
    <!-- Friend list -->
    <div class="flex-1 overflow-y-auto">
      <div v-for="friend in friends" :key="friend.userId"
        class="flex items-center gap-3 px-4 py-3 cursor-pointer hover:bg-[var(--im-bg-secondary)]">
        <div class="im-avatar bg-[var(--im-green)] text-white">
          {{ (friend.nickname || friend.username || '?').charAt(0).toUpperCase() }}
        </div>
        <span class="text-sm">{{ friend.nickname || friend.username }}</span>
      </div>
      <div v-if="friends.length === 0" class="p-8 text-center text-[var(--im-text-secondary)] text-xs">
        {{ $t('contacts.noContacts') }}
      </div>
    </div>
    <!-- Add Friend Dialog -->
    <div v-if="showAddFriend" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div class="bg-[var(--im-surface)] rounded-xl p-6 w-80 shadow-xl">
        <h3 class="text-base font-medium mb-4">{{ $t('contacts.addFriend') }}</h3>
        <input v-model="targetUserId" type="number" placeholder="User ID" class="im-input mb-3" />
        <div class="flex gap-2 justify-end">
          <button @click="showAddFriend = false" class="px-4 py-2 text-sm rounded-lg hover:bg-[var(--im-bg-secondary)]">{{ $t('common.cancel') }}</button>
          <button @click="addFriend" class="im-btn-primary text-sm">{{ $t('common.confirm') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getFriendList, applyFriend } from '../../services/api'

const search = ref('')
const friends = ref<Array<{ userId: number; username: string; nickname: string }>>([])
const showAddFriend = ref(false)
const showCreateGroup = ref(false)
const targetUserId = ref('')

onMounted(async () => {
  try {
    const res = await getFriendList()
    if (res.code === 0) friends.value = res.friends
  } catch {}
})

async function addFriend() {
  const id = Number(targetUserId.value)
  if (id <= 0) return
  try {
    await applyFriend(id)
    showAddFriend.value = false
    const res = await getFriendList()
    if (res.code === 0) friends.value = res.friends
  } catch {}
}
</script>
