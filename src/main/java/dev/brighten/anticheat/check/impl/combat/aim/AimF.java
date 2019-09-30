package dev.brighten.anticheat.check.impl.combat.aim;

import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInFlyingPacket;
import dev.brighten.anticheat.check.api.Check;
import dev.brighten.anticheat.check.api.CheckInfo;
import dev.brighten.anticheat.check.api.CheckType;
import dev.brighten.anticheat.check.api.Packet;

@CheckInfo(name = "Aim (F)", description = "Checks for natural numbers in yaw rotation (near impossible to do consistently).",
        checkType = CheckType.AIM)
public class AimF extends Check {

    @Packet
    public void onFlying(WrappedInFlyingPacket packet) {
        if(!packet.isLook()) return;

        if(data.playerInfo.deltaYaw > 0
                && !data.playerInfo.serverPos
                && Math.abs(data.playerInfo.deltaYaw) % 0.5 == 0) {
            if(vl++ > 12) {
                punish();
            } else if(vl > 4) {
                flag("deltaYaw=" + data.playerInfo.deltaYaw);
            }
        } else vl-= vl > 0 ? 0.25 : 0;
    }
}
