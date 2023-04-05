package ram.talia.hexal.fabric.network

import at.petrak.hexcasting.api.HexAPI.modLoc
import at.petrak.hexcasting.common.network.IMessage
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class MsgPhaseBlockAck(val pos: BlockPos, val duration: Int): IMessage {
    override fun serialize(buf: FriendlyByteBuf) {
        buf.writeInt(pos.x)
        buf.writeInt(pos.y)
        buf.writeInt(pos.z)
        buf.writeInt(duration)
    }

    override fun getFabricId() = ID

    companion object {
        @JvmField
        val ID: ResourceLocation = modLoc("phsblck")

        @JvmStatic
        fun deserialise(buffer: ByteBuf): MsgPhaseBlockAck {
            val buf = FriendlyByteBuf(buffer)
            val pos = BlockPos(buf.readInt(), buf.readInt(), buf.readInt())
            val duration = buf.readInt()
            return MsgPhaseBlockAck(pos, duration)
        }

        @JvmStatic
        fun handle(self: MsgPhaseBlockAck) {
            Minecraft.getInstance().execute {
                val level = Minecraft.getInstance().level ?: return@execute
                level.phaseBlock(self.pos, self.duration)
            }
        }
    }
}