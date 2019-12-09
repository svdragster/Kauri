package dev.brighten.anticheat.data.classes;

import cc.funkemunky.api.Atlas;
import cc.funkemunky.api.utils.BoundingBox;
import cc.funkemunky.api.utils.ReflectionsUtil;
import dev.brighten.anticheat.Kauri;
import dev.brighten.anticheat.data.ObjectData;
import dev.brighten.anticheat.processing.EntityProcessor;
import dev.brighten.anticheat.utils.CollisionHandler;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public class BlockInformation {
    private ObjectData objectData;
    public boolean onClimbable, onSlab, onStairs, onHalfBlock, inLiquid, inLava, inWater, inWeb, onSlime, onIce,
            onSoulSand, blocksAbove, collidesVertically, collidesHorizontally, blocksNear, inBlock;

    public BlockInformation(ObjectData objectData) {
        this.objectData = objectData;
    }

    public void runCollisionCheck() {
        if(!Kauri.INSTANCE.enabled
                || Kauri.INSTANCE.lastEnabled.hasNotPassed(6)) return;
        CollisionHandler handler = new CollisionHandler(objectData);

        List<BoundingBox> boxes = Atlas.getInstance().getBlockBoxManager().getBlockBox().getCollidingBoxes(objectData.getPlayer().getWorld(), objectData.box.grow(1.5f,2,1.5f));

        //Running block checking;
        boxes.parallelStream().forEach(box -> {
            Block block = box.getMinimum().toLocation(objectData.getPlayer().getWorld()).getBlock();

            if(block != null) {
                handler.onCollide(block, box, false);
            }
        });
            //Running entity boundingBox check.
        EntityProcessor.vehicles.computeIfAbsent(objectData.getPlayer().getWorld().getUID(), key -> {
            List<Entity> emptyList = new ArrayList<>();
            EntityProcessor.vehicles.put(key, emptyList);
            return emptyList;
        })
                    .stream()
                    .filter(entity -> entity.getLocation().distance(objectData.getPlayer().getLocation()) < 1.5)
                    .map(entity -> ReflectionsUtil.toBoundingBox(ReflectionsUtil.getBoundingBox(entity)))
                    .forEach(box -> handler.onCollide(null, box, true));

        objectData.playerInfo.serverGround = handler.onGround;
        objectData.playerInfo.nearGround = handler.nearGround;
        objectData.playerInfo.collided =
                (objectData.playerInfo.collidesHorizontally = handler.collidesHorizontally)
                        || (objectData.playerInfo.collidesVertically = handler.collidesVertically);
        onSlab = handler.onSlab;
        onStairs = handler.onStairs;
        onHalfBlock = handler.onHalfBlock;
        inLiquid = handler.inLiquid;
        inBlock = handler.inBlock;
        inWeb = handler.inWeb;
        onSlime = handler.onSlime;
        onIce = handler.onIce;
        onSoulSand = handler.onSoulSand;
        inLava = handler.inLava;
        inWater = handler.inWater;
        blocksAbove = handler.blocksAbove;
        collidesHorizontally = handler.collidesHorizontally;
        collidesVertically = handler.collidesVertically;
        blocksNear = handler.blocksNear;

        boxes.clear();
    }
}