package com.atom.chat.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Fabric's half of the config screen: registers the S2C receivers and hands
 * them to {@link ConfigScreenClient}, which owns the open screen. The bridge
 * stays separate so the common-side payload registration never touches a
 * client-only class.
 */
public final class FabricConfigScreenReceivers {
    private FabricConfigScreenReceivers() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ConfigPayloads.Snapshot.ID, (payload, context) ->
                context.client().execute(() -> ConfigScreenClient.onSnapshot(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ConfigPayloads.Status.ID, (payload, context) ->
                context.client().execute(() -> ConfigScreenClient.onStatus(payload.pack())));
        ClientPlayNetworking.registerGlobalReceiver(ConfigPayloads.Result.ID, (payload, context) ->
                context.client().execute(() -> ConfigScreenClient.onResult(payload)));
    }
}
