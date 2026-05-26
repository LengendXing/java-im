package com.im.client.data.remote

data class ImPacket(
    val cmd: Int,
    val msgType: Int,
    val sequenceId: Int,
    val bodyBytes: ByteArray
) {
    fun encode(): ByteArray {
        val totalLen = Cmd.HEADER_LEN + bodyBytes.size
        val buf = java.nio.ByteBuffer.allocate(totalLen)
        buf.putShort(Cmd.MAGIC.toShort())
        buf.putShort(Cmd.VERSION.toShort())
        buf.putShort(cmd.toShort())
        buf.put(msgType.toByte())
        buf.putInt(sequenceId)
        buf.putInt(bodyBytes.size)
        buf.put(ByteArray(7)) // padding
        buf.put(bodyBytes)
        return buf.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImPacket) return false
        return cmd == other.cmd && msgType == other.msgType &&
                sequenceId == other.sequenceId && bodyBytes.contentEquals(other.bodyBytes)
    }

    override fun hashCode(): Int {
        var result = cmd
        result = 31 * result + msgType
        result = 31 * result + sequenceId
        result = 31 * result + bodyBytes.contentHashCode()
        return result
    }
}
