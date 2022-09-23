package ram.talia.hexal.api.linkable

import at.petrak.hexcasting.api.spell.SpellDatum
import at.petrak.hexcasting.api.spell.Widget
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import ram.talia.hexal.api.nbt.LazyIotaList
import ram.talia.hexal.api.plus
import ram.talia.hexal.api.spell.toNbtList

class PlayerLinkstore(val player: ServerPlayer) : ILinkable<PlayerLinkstore> {
	override val asSpellResult = listOf(SpellDatum.make(player))

	var receivedIotas: MutableList<SpellDatum<*>>
		get() = lazyReceivedIotas.get()
		set(value) = lazyReceivedIotas.set(value)
	private val lazyReceivedIotas: LazyIotaList = LazyIotaList(player.level as ServerLevel)

	var linked: MutableList<ILinkable<*>>
		get() = lazyLinked.get()
		set(value) = lazyLinked.set(value)

	private val lazyLinked = ILinkable.LazyILinkableList(player.level as ServerLevel)

	var renderLinks: MutableList<ILinkable<*>>
		get() {
			return lazyRenderLinks.get()
		}
		set(value) {
			lazyRenderLinks.set(value)

			syncRenderLinks()
		}

	private val lazyRenderLinks: ILinkable.LazyILinkableList = ILinkable.LazyILinkableList(player.level as ServerLevel)

	private fun syncRenderLinks() {
		val compound = CompoundTag()
		compound.put(TAG_RENDER_LINKS, lazyRenderLinks.get().map { LinkableRegistry.wrapSync(it) }.toNbtList())

		// need to actually send this compound somewhere (ugh networking)
		// TODO("not implemented yet")
	}

	private fun addRenderLink(other: ILinkable<*>) {
		renderLinks.add(other)
		syncRenderLinks()
	}

	private fun removeRenderLink(other: ILinkable<*>) {
		renderLinks.remove(other)
		syncRenderLinks()
	}

	fun removeRenderLink(index: Int) {
		renderLinks.removeAt(index)
		syncRenderLinks()
	}

	/**
	 * This should be called every tick to remove links that should be removed (i.e. the entity that is linked to has been removed)
	 */
	fun pruneLinks() {
		for (i in (linked.size - 1) downTo 0) {
			if (linked[i].shouldRemove())
				unlink(linked[i])
		}
	}

	override fun get() = this

	override fun getLinkableType(): LinkableRegistry.LinkableType<PlayerLinkstore, *> = LinkableTypes.PLAYER_LINKSTORE_TYPE

	override fun getPos() = player.position()

	override fun shouldRemove() = player.isRemoved && player.removalReason?.shouldDestroy() == true

	override fun link(other: ILinkable<*>, linkOther: Boolean) {
		if (other in linked || (other is PlayerLinkstore && this.player.uuid.equals(other.player.uuid)))
			return

		linked.add(other)

		if (linkOther) {
			addRenderLink(other)
		}

		if (linkOther) {
			other.link(this, false)
		}
	}

	override fun unlink(other: ILinkable<*>, unlinkOther: Boolean) {
		linked.remove(other)
		removeRenderLink(other)

		if (unlinkOther) {
			other.unlink(this, false)
		}
	}

	override fun getLinked(index: Int) = linked[index]

	override fun getLinkedIndex(linked: ILinkable<*>) = this.linked.indexOf(linked)

	override fun numLinked() = linked.size

	override fun receiveIota(iota: SpellDatum<*>) {
		receivedIotas.add(iota)
	}

	override fun nextReceivedIota(): SpellDatum<*> {
		if (receivedIotas.size == 0) {
			return SpellDatum.make(Widget.NULL)
		}

		val iota = receivedIotas[0]
		receivedIotas.removeAt(0)

		return iota
	}

	override fun numRemainingIota() = linked.size

	override fun writeToNbt(): Tag = NbtUtils.createUUID(player.uuid)

	override fun writeToSync(): Tag = IntTag.valueOf(player.id)

	fun loadAdditionalData(tag: CompoundTag) {
		when (val linkedTag = tag.get(TAG_LINKS) as? ListTag) {
			null -> lazyLinked.set(mutableListOf())
			else -> lazyLinked.set(linkedTag)
		}
		when (val renderLinkedTag = tag.get(TAG_RENDER_LINKS) as? ListTag) {
			null -> lazyRenderLinks.set(mutableListOf())
			else -> lazyRenderLinks.set(renderLinkedTag)
		}
		when (val receivedIotaTag = tag.get(TAG_RECEIVED_IOTAS) as? ListTag) {
			null -> lazyReceivedIotas.set(mutableListOf())
			else -> lazyReceivedIotas.set(receivedIotaTag)
		}
	}

	fun saveAdditionalData(tag: CompoundTag) {
		tag.put(TAG_LINKS, lazyLinked.getUnloaded())
		tag.put(TAG_RENDER_LINKS, lazyRenderLinks.getUnloaded())
		tag.put(TAG_RECEIVED_IOTAS, lazyReceivedIotas.getUnloaded())
	}

	class RenderCentre(val player: AbstractClientPlayer) : ILinkable.IRenderCentre {
		override fun renderCentre(): Vec3 = player.position() + Vec3(0.0, 1.0, 0.0)
	}

	companion object {
		const val TAG_LINKS = "links"
		const val TAG_RENDER_LINKS = "render_links"
		const val TAG_RECEIVED_IOTAS = "received_iotas"
	}

	override fun toString() = "PlayerLinkstore(player=$player)"
}