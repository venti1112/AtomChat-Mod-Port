package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/**
 * Server-media companion payloads (0.2.7). Same-jar dual entrypoint: the
 * records and codecs are registered in the common entrypoint so a dedicated
 * server and the integrated server of a double-open client both speak the
 * protocol.
 *
 * <p>Protocol:
 * <ul>
 *   <li>C2S {@code StateRequest} → S2C {@code MediaState} — is hosting on and
 *       what is the per-file cap.</li>
 *   <li>C2S {@code UploadStart} / {@code UploadChunk} / {@code UploadFinish} →
 *       S2C {@code UploadResult} — chunked upload; the server answers with a
 *       content-addressed {@code mediaId} (or an error).</li>
 *   <li>C2S {@code Fetch} → S2C {@code Data} chunks (or {@code Missing}) —
 *       chunked download over the game connection; no HTTP port involved.</li>
 * </ul>
 *
 * <p>A client that did not negotiate these channels never receives an unknown
 * payload; callers probe with {@code ClientPlayNetworking.canSend} first and
 * silently fall back to the external image host.
 */
public final class MediaPayloads {
    private MediaPayloads() {
    }

    public record StateRequest() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StateRequest> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "media_state_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StateRequest> CODEC =
                StreamCodec.ofMember(StateRequest::write, StateRequest::read);

        private static void write(StateRequest payload, RegistryFriendlyByteBuf buf) {
        }

        private static StateRequest read(RegistryFriendlyByteBuf buf) {
            return new StateRequest();
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record MediaState(boolean enabled, int maxBytes) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<MediaState> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "media_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MediaState> CODEC =
                StreamCodec.ofMember(MediaState::write, MediaState::read);

        private static void write(MediaState payload, RegistryFriendlyByteBuf buf) {
            buf.writeBoolean(payload.enabled());
            buf.writeInt(payload.maxBytes());
        }

        private static MediaState read(RegistryFriendlyByteBuf buf) {
            return new MediaState(buf.readBoolean(), buf.readInt());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record UploadStart(UUID uploadId, String name, int totalBytes) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<UploadStart> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "media_upload_start"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UploadStart> CODEC =
                StreamCodec.ofMember(UploadStart::write, UploadStart::read);

        private static void write(UploadStart payload, RegistryFriendlyByteBuf buf) {
            buf.writeUUID(payload.uploadId());
            buf.writeUtf(payload.name() == null ? "" : payload.name());
            buf.writeInt(payload.totalBytes());
        }

        private static UploadStart read(RegistryFriendlyByteBuf buf) {
            return new UploadStart(buf.readUUID(), buf.readUtf(), buf.readInt());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record UploadChunk(UUID uploadId, int offset, byte[] data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<UploadChunk> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "media_upload_chunk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UploadChunk> CODEC =
                StreamCodec.ofMember(UploadChunk::write, UploadChunk::read);

        private static void write(UploadChunk payload, RegistryFriendlyByteBuf buf) {
            buf.writeUUID(payload.uploadId());
            buf.writeInt(payload.offset());
            buf.writeByteArray(payload.data());
        }

        private static UploadChunk read(RegistryFriendlyByteBuf buf) {
            return new UploadChunk(buf.readUUID(), buf.readInt(), buf.readByteArray());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record UploadFinish(UUID uploadId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<UploadFinish> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "media_upload_finish"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UploadFinish> CODEC =
                StreamCodec.ofMember(UploadFinish::write, UploadFinish::read);

        private static void write(UploadFinish payload, RegistryFriendlyByteBuf buf) {
            buf.writeUUID(payload.uploadId());
        }

        private static UploadFinish read(RegistryFriendlyByteBuf buf) {
            return new UploadFinish(buf.readUUID());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** {@code mediaId} and {@code error} are empty strings rather than null. */
    public record UploadResult(UUID uploadId, String mediaId, String error) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<UploadResult> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "media_upload_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UploadResult> CODEC =
                StreamCodec.ofMember(UploadResult::write, UploadResult::read);

        private static void write(UploadResult payload, RegistryFriendlyByteBuf buf) {
            buf.writeUUID(payload.uploadId());
            buf.writeUtf(payload.mediaId() == null ? "" : payload.mediaId());
            buf.writeUtf(payload.error() == null ? "" : payload.error());
        }

        private static UploadResult read(RegistryFriendlyByteBuf buf) {
            return new UploadResult(buf.readUUID(), buf.readUtf(), buf.readUtf());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record Fetch(String mediaId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Fetch> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "media_fetch"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Fetch> CODEC =
                StreamCodec.ofMember(Fetch::write, Fetch::read);

        private static void write(Fetch payload, RegistryFriendlyByteBuf buf) {
            buf.writeUtf(payload.mediaId() == null ? "" : payload.mediaId());
        }

        private static Fetch read(RegistryFriendlyByteBuf buf) {
            return new Fetch(buf.readUtf());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** One streamed slice; {@code offset + data.length == totalBytes} marks the end. */
    public record Data(String mediaId, int offset, int totalBytes, byte[] data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Data> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "media_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> CODEC =
                StreamCodec.ofMember(Data::write, Data::read);

        private static void write(Data payload, RegistryFriendlyByteBuf buf) {
            buf.writeUtf(payload.mediaId() == null ? "" : payload.mediaId());
            buf.writeInt(payload.offset());
            buf.writeInt(payload.totalBytes());
            buf.writeByteArray(payload.data());
        }

        private static Data read(RegistryFriendlyByteBuf buf) {
            return new Data(buf.readUtf(), buf.readInt(), buf.readInt(), buf.readByteArray());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record Missing(String mediaId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Missing> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "media_missing"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Missing> CODEC =
                StreamCodec.ofMember(Missing::write, Missing::read);

        private static void write(Missing payload, RegistryFriendlyByteBuf buf) {
            buf.writeUtf(payload.mediaId() == null ? "" : payload.mediaId());
        }

        private static Missing read(RegistryFriendlyByteBuf buf) {
            return new Missing(buf.readUtf());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Registers every codec; must run on both logical sides (common init). */
    public static void register() {
        PayloadTypeRegistry.playC2S().register(StateRequest.ID, StateRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(UploadStart.ID, UploadStart.CODEC);
        PayloadTypeRegistry.playC2S().register(UploadChunk.ID, UploadChunk.CODEC);
        PayloadTypeRegistry.playC2S().register(UploadFinish.ID, UploadFinish.CODEC);
        PayloadTypeRegistry.playC2S().register(Fetch.ID, Fetch.CODEC);
        PayloadTypeRegistry.playS2C().register(MediaState.ID, MediaState.CODEC);
        PayloadTypeRegistry.playS2C().register(UploadResult.ID, UploadResult.CODEC);
        PayloadTypeRegistry.playS2C().register(Data.ID, Data.CODEC);
        PayloadTypeRegistry.playS2C().register(Missing.ID, Missing.CODEC);
    }
}
