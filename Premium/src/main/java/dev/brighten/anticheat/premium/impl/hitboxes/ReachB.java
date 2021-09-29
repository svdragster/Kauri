package dev.brighten.anticheat.premium.impl.hitboxes;

import cc.funkemunky.api.tinyprotocol.api.ProtocolVersion;
import cc.funkemunky.api.tinyprotocol.api.TinyProtocolHandler;
import cc.funkemunky.api.tinyprotocol.api.packets.channelhandler.TinyProtocol1_8;
import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInFlyingPacket;
import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInTransactionPacket;
import cc.funkemunky.api.tinyprotocol.packet.in.WrappedInUseEntityPacket;
import cc.funkemunky.api.tinyprotocol.packet.out.WrappedOutEntityTeleportPacket;
import cc.funkemunky.api.tinyprotocol.packet.out.WrappedOutRelativePosition;
import cc.funkemunky.api.utils.KLocation;
import cc.funkemunky.api.utils.MathUtils;
import cc.funkemunky.api.utils.world.EntityData;
import cc.funkemunky.api.utils.world.types.SimpleCollisionBox;
import dev.brighten.anticheat.Kauri;
import dev.brighten.anticheat.check.api.Check;
import dev.brighten.anticheat.check.api.CheckInfo;
import dev.brighten.anticheat.check.api.Packet;
import dev.brighten.anticheat.data.ObjectData;
import dev.brighten.anticheat.utils.AxisAlignedBB;
import dev.brighten.anticheat.utils.EntityLocation;
import dev.brighten.anticheat.utils.MiscUtils;
import dev.brighten.anticheat.utils.Vec3D;
import dev.brighten.anticheat.utils.timer.Timer;
import dev.brighten.anticheat.utils.timer.impl.AtlasTimer;
import dev.brighten.anticheat.utils.timer.impl.PlayerTimer;
import dev.brighten.anticheat.utils.timer.impl.TickTimer;
import dev.brighten.api.KauriVersion;
import dev.brighten.api.check.CheckType;
import lombok.val;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.*;

@CheckInfo(name = "Reach (B)", planVersion = KauriVersion.ARA, developer = true, checkType = CheckType.HITBOX)
public class ReachB extends Check {

    private EntityLocation eloc = new EntityLocation(UUID.randomUUID()), eloc2 = new EntityLocation(UUID.randomUUID());
    private Timer lastFlying = new TickTimer();
    private int streak;
    private float buffer;
    private boolean sentTeleport, attacked;

    @Override
    public void setData(ObjectData data) {
        super.setData(data);
    }

    @Packet
    public void onUse(WrappedInUseEntityPacket packet) {
        if(data.target == null || packet.getAction() != WrappedInUseEntityPacket.EnumEntityUseAction.ATTACK)
            return;

        //Updating new entity loc
        if(data.target.getUniqueId() != eloc.uuid) {
            eloc = new EntityLocation(data.target.getUniqueId());
            eloc2 = new EntityLocation(data.target.getUniqueId());
            sentTeleport = false;
        }

        final KLocation eyeLoc = data.playerInfo.to.clone();

        eyeLoc.y+= data.playerInfo.sneaking ? 1.54f : 1.62f;

        if(eloc.x == 0 && eloc.y == 0 & eloc.z == 0) return;

        SimpleCollisionBox targetBox = (SimpleCollisionBox) EntityData
                .getEntityBox(new Vector(eloc.x, eloc.y, eloc.z), data.target),
                targetBox2 = (SimpleCollisionBox) EntityData
                        .getEntityBox(new Vector(eloc2.x, eloc2.y, eloc2.z), data.target);

        if(data.playerVersion.isBelow(ProtocolVersion.V1_9)) {
            targetBox = targetBox.expand(0.1, 0.1, 0.1);
            targetBox2 = targetBox2.expand(0.1, 0.1, 0.1);
        }

        final AxisAlignedBB vanillaBox = new AxisAlignedBB(targetBox), vanillaBox2 = new AxisAlignedBB(targetBox2);

        Vec3D intersect = vanillaBox.rayTrace(eyeLoc.toVector(), MathUtils.getDirection(eyeLoc), 10),
        intersect2 = vanillaBox2.rayTrace(eyeLoc.toVector(), MathUtils.getDirection(eyeLoc), 10);

        double distance = Double.MAX_VALUE;
        boolean collided = false; //Using this to compare smaller numbers than Double.MAX_VALUE. Slightly faster

        if(intersect != null) {
            distance = intersect.distanceSquared(new Vec3D(eyeLoc.x, eyeLoc.y, eyeLoc.z));
            collided = true;
        }

        if(intersect2 != null) {
            distance = Math.min(distance, intersect2.distanceSquared(new Vec3D(eyeLoc.x, eyeLoc.y, eyeLoc.z)));
            collided = true;
        }

        if(collided) {
            distance = Math.sqrt(distance);
            if(distance > 3.02 && streak > 3 && sentTeleport && lastFlying.isNotPassed(1)) {
                if(++buffer > 3) {
                    vl++;
                    flag("d=%.4f", distance);
                    buffer = 3;
                }
            } else if(buffer > 0) buffer-= 0.05f;
            debug("dist=%.2f b=%s s=%s st=%s lf=%s",
                    distance, buffer, streak, sentTeleport, lastFlying.getPassed());
        } else debug("didnt hit box: x=%.1f y=%.1f z=%.1f", eloc.x, eloc.y, eloc.z);
    }

    @Packet
    public void onFlying(WrappedInFlyingPacket packet) {
        eloc.interpolateLocation();
        eloc2.interpolateLocation();
        if(lastFlying.isNotPassed(2)) streak++;
        else {
            streak = 1;
            sentTeleport = false;
        }

        lastFlying.reset();
    }

    @Packet
    public void onTrans(WrappedInTransactionPacket packet) {
        if(lastFlying.isPassed(1)
                && Kauri.INSTANCE.keepaliveProcessor.getKeepById(packet.getAction()).isPresent()) {
            eloc.interpolateLocation();
            eloc2.interpolateLocation();
            attacked = false;
        }
    }

    private boolean resentTeleport = false, sentSecondAction = false, resentRel = false, sentSecondAction2 = false;

    @Packet
    public boolean onEntityCancellable(WrappedOutRelativePosition packet, long sent) {
        if(data.target != null && data.target.getEntityId() == packet.getId()) {
            if(!resentRel) {
                sentSecondAction2 = false;
                data.runInstantAction(() -> {
                    //We don't need to do version checking here. Atlas handles this for us.
                    if(ProtocolVersion.getGameVersion().isBelow(ProtocolVersion.V1_9)) {
                        eloc.newX += (byte)packet.getX() / 32D;
                        eloc.newY += (byte)packet.getY() / 32D;
                        eloc.newZ += (byte)packet.getZ() / 32D;
                        eloc.newYaw += (float)(byte)packet.getYaw() / 256.0F * 360.0F;
                        eloc.newPitch += (float)(byte)packet.getPitch() / 256.0F * 360.0F;
                    } else if(ProtocolVersion.getGameVersion().isBelow(ProtocolVersion.V1_14)) {
                        eloc.newX += (long)packet.getX() / 4096D;
                        eloc.newY += (long)packet.getY() / 4096D;
                        eloc.newZ += (long)packet.getZ() / 4096D;
                        eloc.newYaw += (float)(byte)packet.getYaw() / 256.0F * 360.0F;
                        eloc.newPitch += (float)(byte)packet.getPitch() / 256.0F * 360.0F;
                    } else {
                        eloc.newX += (short)packet.getX() / 4096D;
                        eloc.newY += (short)packet.getY() / 4096D;
                        eloc.newZ += (short)packet.getZ() / 4096D;
                        eloc.newYaw += (float)(byte)packet.getYaw() / 256.0F * 360.0F;
                        eloc.newPitch += (float)(byte)packet.getPitch() / 256.0F * 360.0F;
                    }

                    eloc.increment = 3;

                    long delta = System.currentTimeMillis() - sent;

                    resentRel = false;
                    debug("relmove #1: %s", delta);
                });
                
                

                resentRel = true;
                if(!sentSecondAction2) {
                    if(ProtocolVersion.getGameVersion().isBelow(ProtocolVersion.V1_9)) {
                        byte x = packet.getX(), y = packet.getY(), z = packet.getZ(), yaw =
                                packet.getYaw(), pitch = packet.getPitch();

                        TinyProtocolHandler.sendPacket(data.getPlayer(), new WrappedOutRelativePosition(packet.getId(),
                                x / 32D, y / 32D, z / 32D, yaw / 256.0F * 360.0F,
                                pitch / 256.0F * 360.0F, packet.isGround()).getObject());
                    } else if(ProtocolVersion.getGameVersion().isBelow(ProtocolVersion.V1_14)) {
                        long x = packet.getX(), y = packet.getY(), z = packet.getZ(), yaw =
                                packet.getYaw(), pitch = packet.getPitch();

                        TinyProtocolHandler.sendPacket(data.getPlayer(), new WrappedOutRelativePosition(packet.getId(),
                                x / 4069D, y / 4069D, z / 4069D, yaw / 256.0F * 360.0F,
                                pitch / 256.0F * 360.0F, packet.isGround()).getObject());
                    } else {
                        short x = packet.getX(), y = packet.getY(), z = packet.getZ(), yaw =
                                packet.getYaw(), pitch = packet.getPitch();

                        TinyProtocolHandler.sendPacket(data.getPlayer(), new WrappedOutRelativePosition(packet.getId(),
                                x / 4069D, y / 4069D, z / 4069D, yaw / 256.0F * 360.0F,
                                pitch / 256.0F * 360.0F, packet.isGround()).getObject());
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Packet
    public void onEntity(WrappedOutRelativePosition packet, long sent) {
        if(data.target != null && data.target.getEntityId() == packet.getId()) {
            if(!sentSecondAction2)
            data.runInstantAction(() -> {
                //We don't need to do version checking here. Atlas handles this for us.
                if(ProtocolVersion.getGameVersion().isBelow(ProtocolVersion.V1_9)) {
                    eloc2.newX += (byte)packet.getX() / 32D;
                    eloc2.newY += (byte)packet.getY() / 32D;
                    eloc2.newZ += (byte)packet.getZ() / 32D;
                    eloc2.newYaw += (float)(byte)packet.getYaw() / 256.0F * 360.0F;
                    eloc2.newPitch += (float)(byte)packet.getPitch() / 256.0F * 360.0F;
                } else if(ProtocolVersion.getGameVersion().isBelow(ProtocolVersion.V1_14)) {
                    eloc2.newX += (long)packet.getX() / 4096D;
                    eloc2.newY += (long)packet.getY() / 4096D;
                    eloc2.newZ += (long)packet.getZ() / 4096D;
                    eloc2.newYaw += (float)(byte)packet.getYaw() / 256.0F * 360.0F;
                    eloc2.newPitch += (float)(byte)packet.getPitch() / 256.0F * 360.0F;
                } else {
                    eloc2.newX += (short)packet.getX() / 4096D;
                    eloc2.newY += (short)packet.getY() / 4096D;
                    eloc2.newZ += (short)packet.getZ() / 4096D;
                    eloc2.newYaw += (float)(byte)packet.getYaw() / 256.0F * 360.0F;
                    eloc2.newPitch += (float)(byte)packet.getPitch() / 256.0F * 360.0F;
                }

                eloc2.increment = 3;

                long delta = System.currentTimeMillis() - sent;

                debug("relmove #2: %s", delta);
            });
            sentSecondAction2 = true;
        }
    }

    private List<WrappedOutEntityTeleportPacket> packets = new ArrayList<>();

    @Packet
    public void onTeleportSent(WrappedOutEntityTeleportPacket packet, long sent) {
        if(!sentSecondAction)
        data.runInstantAction(() -> {
            if(data.playerVersion.isOrAbove(ProtocolVersion.V1_9)) {
                if (!(Math.abs(eloc2.x - packet.x) >= 0.03125D)
                        && !(Math.abs(eloc2.y - packet.y) >= 0.015625D)
                        && !(Math.abs(eloc2.z - packet.z) >= 0.03125D)) {
                    eloc2.increment = 0;
                    //We don't need to do version checking here. Atlas handles this for us.
                    eloc2.newX = eloc2.x = packet.x;
                    eloc2.newY = eloc2.y = packet.y;
                    eloc2.newZ = eloc2.z = packet.z;
                    eloc2.newYaw = eloc2.yaw = packet.yaw;
                    eloc2.newPitch = eloc2.pitch = packet.pitch;
                } else {
                    eloc2.newX = packet.x;
                    eloc2.newY = packet.y;
                    eloc2.newZ = packet.z;
                    eloc2.newYaw = packet.yaw;
                    eloc2.newPitch = packet.pitch ;

                    eloc2.increment = 3;
                }
            } else {
                //We don't need to do version checking here. Atlas handles this for us.
                eloc2.newX = packet.x;
                eloc2.newY = packet.y;
                eloc2.newZ = packet.z;
                eloc2.newYaw = packet.yaw;
                eloc2.newPitch = packet.pitch ;

                eloc2.increment = 3;
            }

            long delta = System.currentTimeMillis() - sent;

            debug("teleport #2: %s", delta);
        });

        sentSecondAction = true;
    }

    @Packet
    public boolean onTeleport(WrappedOutEntityTeleportPacket packet, long sent) {
        if(data.target != null && data.target.getEntityId() == packet.entityId) {
           
           if(!resentTeleport) {
               sentSecondAction = false;
               data.runInstantAction(() -> {
                   if(data.playerVersion.isOrAbove(ProtocolVersion.V1_9)) {
                       if (!(Math.abs(eloc.x - packet.x) >= 0.03125D)
                               && !(Math.abs(eloc.y - packet.y) >= 0.015625D)
                               && !(Math.abs(eloc.z - packet.z) >= 0.03125D)) {
                           eloc.increment = 0;
                           //We don't need to do version checking here. Atlas handles this for us.
                           eloc.newX = eloc.x = packet.x;
                           eloc.newY = eloc.y = packet.y;
                           eloc.newZ = eloc.z = packet.z;
                           eloc.newYaw = eloc.yaw = packet.yaw;
                           eloc.newPitch = eloc.pitch = packet.pitch;
                       } else {
                           eloc.newX = packet.x;
                           eloc.newY = packet.y;
                           eloc.newZ = packet.z;
                           eloc.newYaw = packet.yaw;
                           eloc.newPitch = packet.pitch ;

                           eloc.increment = 3;
                       }
                   } else {
                       //We don't need to do version checking here. Atlas handles this for us.
                       eloc.newX = packet.x;
                       eloc.newY = packet.y;
                       eloc.newZ = packet.z;
                       eloc.newYaw = packet.yaw;
                       eloc.newPitch = packet.pitch ;

                       eloc.increment = 3;
                   }

                   resentTeleport = false;
                   long delta = System.currentTimeMillis() - sent;

                   debug("teleport #1: %s", delta);
               });

               resentTeleport = true;
               if(!sentSecondAction)
               TinyProtocolHandler.sendPacket(data.getPlayer(), new WrappedOutEntityTeleportPacket(packet.entityId, packet.x, packet.y, packet.z,
                       packet.yaw, packet.pitch, packet.onGround).getObject());
               return true;
           }
        }
        return false;
    }

}
