package dev.brighten.anticheat.premium.impl;

import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInFlyingPacket;
import dev.brighten.anticheat.check.api.Check;
import dev.brighten.anticheat.check.api.CheckInfo;
import dev.brighten.anticheat.check.api.Packet;
import dev.brighten.api.check.CheckType;

@CheckInfo(name = "Aim (H)", description = "Checks for any low outliers in deltayaw.",
        developer = true, checkType = CheckType.AIM, vlToFlag = 9)
public class AimH extends Check {
    private double lastPosX, lastPosZ, lastHorizontalDistance;
    private float lastYaw, lastPitch;

    @Packet
    public void process(final WrappedInFlyingPacket packet, final long current) {
        final double posX = packet.getX();
        final double posZ = packet.getZ();

        final float yaw = packet.getYaw();
        final float pitch = packet.getPitch();

        final double horizontalDistance = Math.hypot(posX - lastPosX, posZ - lastPosZ);

        // Player moved
        if (posX != lastPosX || posZ != lastPosZ) {
            final float deltaYaw = Math.abs(yaw - lastYaw);
            final float deltaPitch = Math.abs(pitch - lastPitch);

            final boolean attacking = current - data.playerInfo.lastAttackTimeStamp < 100L;
            final double acceleration = Math.abs(horizontalDistance - lastHorizontalDistance);

            // Player made a large head rotation and didn't accelerate / decelerate which is impossible
            if (acceleration < 1e-02 && deltaYaw > 30.f && deltaPitch > 15.f && attacking) {
                vl++;
                flag("accel=%v.2 deltaYaw=%v.2 deltaPitch=%v.2 attacking=%v",
                        acceleration, deltaYaw, deltaPitch, attacking);
            }
        }

        this.lastHorizontalDistance = horizontalDistance;
        this.lastYaw = yaw;
        this.lastPitch = pitch;
        this.lastPosX = posX;
        this.lastPosZ = posZ;
    }
}