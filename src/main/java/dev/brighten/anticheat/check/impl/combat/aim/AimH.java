package dev.brighten.anticheat.check.impl.combat.aim;

import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInFlyingPacket;
import cc.funkemunky.api.utils.MathUtils;
import dev.brighten.anticheat.check.api.Check;
import dev.brighten.anticheat.check.api.CheckInfo;
import dev.brighten.anticheat.check.api.CheckType;
import dev.brighten.anticheat.check.api.Packet;

@CheckInfo(name = "Aim (H)", description = "Ensures that yaw and pitch acceleration is legitimate.",
        checkType = CheckType.AIM, developer = true)
public class AimH extends Check {
    @Packet
    public void onFlying(WrappedInFlyingPacket packet) {
        if(packet.isLook()) {
            float yawAccel = MathUtils.getDelta(data.playerInfo.lDeltaYaw, data.playerInfo.deltaYaw);
            float pitchAccel = MathUtils.getDelta(data.playerInfo.lDeltaPitch, data.playerInfo.deltaPitch);

            if(yawAccel < 1E-3 && pitchAccel < 1E-4 && data.playerInfo.deltaYaw > 1) {
                if(vl++ > 60) {
                    punish();
                } else if(vl > 25) {
                    flag("yawAccel=" + yawAccel + " pitchAccel=" + pitchAccel);
                }
            } else vl-= vl > 0 ? 5 : 0;

            debug("yaw=" + yawAccel + " pitch=" + pitchAccel + " vl=" + vl
                    + " yd=" + data.playerInfo.deltaYaw);
        }
    }
}
