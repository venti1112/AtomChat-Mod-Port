package com.atom.chat.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The client half of the pack seam: where the shared logic meets Fabric.
 *
 * <p>Send port and client facts only - the payload conversion lives with the
 * payload types (see {@link PackPayloads}), and this class names client types
 * freely: nothing references it except the client entrypoint.
 *
 * <p>Fabric registers the S2C receivers here rather than in the payload types.
 */
public final class PackNetClient implements Net.ClientSide, Host.ClientSide {

    /** Installed from the client entrypoint, before any payload can arrive. */
    public static void install() {
        PackNetClient impl = new PackNetClient();
        Net.installClientSide(impl);
        Host.installClientSide(impl);
        ClientPlayNetworking.registerGlobalReceiver(PackPayloads.Manifest.ID,
                (payload, context) -> context.client().execute(
                        () -> PackSyncClient.onManifest(PackPayloads.toMessage(payload))));
        ClientPlayNetworking.registerGlobalReceiver(PackPayloads.Chunk.ID,
                (payload, context) -> context.client().execute(
                        () -> PackSyncClient.onChunk(PackPayloads.toMessage(payload))));
        ClientPlayNetworking.registerGlobalReceiver(PackPayloads.Done.ID,
                (payload, context) -> context.client().execute(
                        () -> PackSyncClient.onDone(payload.fileCount())));
    }

    private PackNetClient() {
    }

    @Override
    public void sendToServer(PackMessage.C2S message) {
        ClientPlayNetworking.send(PackPayloads.fromMessage(message));
    }

    /**
     * Fabric registers one channel per payload, so the probe has to name the kind
     * it means - unlike Forge, where the three share a single channel.
     */
    @Override
    public boolean canSendToServer(PackMessage.C2S message) {
        return ClientPlayNetworking.canSend(channelOf(message));
    }

    @Override
    public void execute(Runnable work) {
        Minecraft.getInstance().execute(work);
    }

    @Override
    public String serverAddress() {
        ServerData info = Minecraft.getInstance().getCurrentServer();
        return info == null ? null : info.ip;
    }

    @Override
    public String levelName() {
        Minecraft client = Minecraft.getInstance();
        return client.getSingleplayerServer() == null
                ? null
                : client.getSingleplayerServer().getWorldData().getLevelName();
    }

    private static CustomPacketPayload.Type<?> channelOf(PackMessage.C2S message) {
        return switch (message) {
            case PackMessage.Hello ignored -> PackPayloads.Hello.ID;
            case PackMessage.Need ignored -> PackPayloads.Need.ID;
            case PackMessage.Ack ignored -> PackPayloads.Ack.ID;
        };
    }
}
