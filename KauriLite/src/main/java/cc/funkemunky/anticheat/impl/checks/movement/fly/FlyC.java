package cc.funkemunky.anticheat.impl.checks.movement.fly;

import cc.funkemunky.anticheat.api.checks.CancelType;
import cc.funkemunky.anticheat.api.checks.Check;
import cc.funkemunky.anticheat.api.checks.CheckInfo;
import cc.funkemunky.anticheat.api.checks.CheckType;
import cc.funkemunky.anticheat.api.utils.MiscUtils;
import cc.funkemunky.anticheat.api.utils.Packets;
import cc.funkemunky.api.tinyprotocol.api.Packet;
import cc.funkemunky.api.utils.MathUtils;
import lombok.val;
import org.bukkit.event.Event;

@Packets(packets = {Packet.Client.LEGACY_POSITION, Packet.Client.LEGACY_POSITION_LOOK, Packet.Client.POSITION_LOOK, Packet.Client.POSITION})
@cc.funkemunky.api.utils.Init
@CheckInfo(name = "Fly (Type C)", description = "Makes sure the client is accelerating towards the ground properly.", type = CheckType.FLY, cancelType = CancelType.MOTION, maxVL = 200)
public class FlyC extends Check {

    private double lastYChange;
    private int vl;

    @Override
    public void onPacket(Object packet, String packetType, long timeStamp) {
        val move = getData().getMovementProcessor();
        val from = move.getFrom();
        val to = move.getTo();

        val yChange = to.getY() - from.getY();
        val predictedY = (lastYChange - 0.08D) * 0.9800000190734863D;
        this.lastYChange = yChange;

        if (MiscUtils.cancelForFlight(getData(), 15, true)) return;

        if (!move.isBlocksAround()) {
            val offset = Math.abs(yChange - predictedY);

            if (!MathUtils.approxEquals(0.05, yChange, predictedY)) {
                if (vl++ > 2) {
                    this.flag("O -> " + offset, false, true);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }

            debug("VL: " + vl + "DIF: " + Math.abs(yChange - predictedY));
        }
    }

    @Override
    public void onBukkitEvent(Event event) {

    }
}