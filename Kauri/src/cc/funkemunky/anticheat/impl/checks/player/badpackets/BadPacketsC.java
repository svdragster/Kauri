package cc.funkemunky.anticheat.impl.checks.player.badpackets;

import cc.funkemunky.anticheat.api.checks.CancelType;
import cc.funkemunky.anticheat.api.checks.Check;
import cc.funkemunky.anticheat.api.checks.CheckInfo;
import cc.funkemunky.anticheat.api.checks.CheckType;
import cc.funkemunky.anticheat.api.utils.Packets;
import cc.funkemunky.api.tinyprotocol.api.Packet;
import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInFlyingPacket;
import lombok.val;
import org.bukkit.event.Event;

@Packets(packets = {Packet.Client.LOOK, Packet.Client.POSITION_LOOK, Packet.Client.LEGACY_LOOK, Packet.Client.LEGACY_POSITION_LOOK})
@cc.funkemunky.api.utils.Init
@CheckInfo(name = "BadPackets (Type C)", description = "Checks for impossible pitch rotation.", type = CheckType.BADPACKETS, maxVL = 2)
public class BadPacketsC extends Check {
    public BadPacketsC() {

    }

    @Override
    public void onPacket(Object packet, String packetType, long timeStamp) {
        WrappedInFlyingPacket flying = new WrappedInFlyingPacket(packet, getData().getPlayer());

        val pitch = Math.abs(flying.getPitch());

        if (pitch > 90) {
            flag(pitch + ">-90", true, true);
        }
    }

    @Override
    public void onBukkitEvent(Event event) {

    }
}