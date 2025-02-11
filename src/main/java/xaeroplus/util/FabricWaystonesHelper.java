package xaeroplus.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import wraith.fwaystones.FabricWaystones;
import wraith.fwaystones.access.WaystoneValue;
import xaeroplus.module.impl.WaystoneSync;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

// Need to move out code that references FabricWaystones classes due to EventBus classloading shenanigans
public class FabricWaystonesHelper {

    public static List<WaystoneSync.Waystone> getWaystones() {
        var waystoneStorage = FabricWaystones.WAYSTONE_STORAGE;
        if (waystoneStorage == null) return Collections.emptyList();
        ConcurrentHashMap<String, WaystoneValue> waystones = waystoneStorage.WAYSTONES;
        if (waystones == null) return Collections.emptyList();
        return waystones.values().stream()
            .map(waystone -> new WaystoneSync.Waystone(waystone.getWaystoneName(),
                                                       ResourceKey.create(Registries.DIMENSION, new ResourceLocation(waystone.getWorldName())),
                                                       waystone.way_getPos().getX(),
                                                       waystone.way_getPos().getY() + 1,// avoid teleporting directly into the waystone
                                                       waystone.way_getPos().getZ()))
            .toList();
    }
}
