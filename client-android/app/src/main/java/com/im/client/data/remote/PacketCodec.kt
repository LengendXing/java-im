package com.im.client.data.remote

import java.nio.ByteBuffer

object PacketCodec {

    fun decode(buf: ByteArray, offset: Int, length: Int): ImPacket? {
        if (length < Cmd.HEADER_LEN) return null

        val bb = ByteBuffer.wrap(buf, offset, length)
        val magic = bb.short.toInt() and 0xFFFF
        if (magic != Cmd.MAGIC) return null

        bb.short // skip version
        val cmd = bb.short.toInt() and 0xFFFF
        val msgType = bb.get().toInt() and 0xFF
        val sequenceId = bb.int
        val dataLength = bb.int
        bb.get(ByteArray(7)) // skip padding

        if (length < Cmd.HEADER_LEN + dataLength) return null

        val bodyBytes = ByteArray(dataLength)
        bb.get(bodyBytes)
        return ImPacket(cmd, msgType, sequenceId, bodyBytes)
    }

    fun decode(buf: ByteArray): ImPacket? = decode(buf, 0, buf.size)
}
