package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/**
 * Avatar-companion payloads (0.1.10). Same-jar dual entrypoint: the records
 * and codecs are registered in the common entrypoint so both the integrated
 * server of a double-open client and a dedicated server speak the protocol.
 *
 * <p>Protocol:
 * <ul>
 *   <li>C2S {@code upload} — the local player pushed their custom avatar
 *       (uuid must equal the sender, validated server-side).</li>
 *   <li>C2S {@code request} — the client needs the avatar of {@code uuid}
 *       (lazy loading; the server keeps no state).</li>
 *   <li>S2C {@code data} — the requested avatar's PNG bytes, or an empty
 *       array when the uuid has no custom avatar (negative answer).</li>
 * </ul>
 *
 * <p>The client only sends on channels the server actually negotiated; servers
 * without this companion therefore never receive an unknown payload and the
 * client silently degrades to skins (e33chat philosophy).
 */
public final class AvatarPayloads {
    private AvatarPayloads() {
    }

    public static final int MAX_AVATAR_BYTES = 256 * 1024;

    public record AvatarUploadPayload(UUID uuid, byte[] data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AvatarUploadPayload> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "avatar_upload"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AvatarUploadPayload> CODEC =
                StreamCodec.ofMember(AvatarUploadPayload::write, AvatarUploadPayload::read);

        private static void write(AvatarUploadPayload payload, RegistryFriendlyByteBuf buf) {
            buf.writeUUID(payload.uuid());
            buf.writeByteArray(payload.data());
        }

        private static AvatarUploadPayload read(RegistryFriendlyByteBuf buf) {
            return new AvatarUploadPayload(buf.readUUID(), buf.readByteArray());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record AvatarRequestPayload(UUID uuid) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AvatarRequestPayload> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "avatar_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AvatarRequestPayload> CODEC =
                StreamCodec.ofMember(AvatarRequestPayload::write, AvatarRequestPayload::read);

        private static void write(AvatarRequestPayload payload, RegistryFriendlyByteBuf buf) {
            buf.writeUUID(payload.uuid());
        }

        private static AvatarRequestPayload read(RegistryFriendlyByteBuf buf) {
            return new AvatarRequestPayload(buf.readUUID());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Empty {@code data} = the uuid has no custom avatar. */
    public record AvatarDataPayload(UUID uuid, byte[] data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AvatarDataPayload> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "avatar_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AvatarDataPayload> CODEC =
                StreamCodec.ofMember(AvatarDataPayload::write, AvatarDataPayload::read);

        private static void write(AvatarDataPayload payload, RegistryFriendlyByteBuf buf) {
            buf.writeUUID(payload.uuid());
            buf.writeByteArray(payload.data());
        }

        private static AvatarDataPayload read(RegistryFriendlyByteBuf buf) {
            return new AvatarDataPayload(buf.readUUID(), buf.readByteArray());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * S2C notification that {@code uuid}'s stored avatar just changed. A
     * session-wide cache has no rejoin to lean on (0.2.5 hunt: an uploaded
     * avatar never reached players who had already negative-cached or decoded
     * the older copy), so every successful upload is announced and receivers
     * drop that uuid's cache before the next frame re-requests it.
     */
    public record AvatarChangedPayload(UUID uuid) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AvatarChangedPayload> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "avatar_changed"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AvatarChangedPayload> CODEC =
                StreamCodec.ofMember(AvatarChangedPayload::write, AvatarChangedPayload::read);

        private static void write(AvatarChangedPayload payload, RegistryFriendlyByteBuf buf) {
            buf.writeUUID(payload.uuid());
        }

        private static AvatarChangedPayload read(RegistryFriendlyByteBuf buf) {
            return new AvatarChangedPayload(buf.readUUID());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Registers every codec; must run on both logical sides (common init). */
    public static void register() {
        PayloadTypeRegistry.playC2S().register(AvatarUploadPayload.ID, AvatarUploadPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AvatarRequestPayload.ID, AvatarRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AvatarDataPayload.ID, AvatarDataPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AvatarChangedPayload.ID, AvatarChangedPayload.CODEC);
    }
}
