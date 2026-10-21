package com.atom.chat.net;
import com.atom.chat.AtomChat;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

import com.atom.chat.profile.ProfilePatch;

/** Separate optional profile channel; avatar wire IDs stay untouched. */
public final class ProfilePayloads {
    public record ProfileUpdatePayload(long token, ProfilePatch patch) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ProfileUpdatePayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "profile_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ProfileUpdatePayload> CODEC = StreamCodec.ofMember(ProfileUpdatePayload::write, ProfileUpdatePayload::read);
        private static void write(ProfileUpdatePayload payload, RegistryFriendlyByteBuf buf) { buf.writeLong(payload.token());
            int flags = (payload.patch().signature() != null ? 1 : 0) | (payload.patch().avatar() != null ? 2 : 0) | (payload.patch().banner() != null ? 4 : 0);
            buf.writeInt(flags);
            if ((flags & 1) != 0) buf.writeUtf(payload.patch().signature(), 256);
            if ((flags & 2) != 0) buf.writeUtf(payload.patch().avatar(), 2048);
            if ((flags & 4) != 0) buf.writeUtf(payload.patch().banner(), 2048); }
        private static ProfileUpdatePayload read(RegistryFriendlyByteBuf buf) { long token = buf.readLong();
            int flags = buf.readInt();
            if (flags == 0 || (flags & ~7) != 0) throw new IllegalArgumentException("invalid_profile_patch_flags");
            return new ProfileUpdatePayload(token, new ProfilePatch((flags & 1) != 0 ? buf.readUtf(256) : null,
                    (flags & 2) != 0 ? buf.readUtf(2048) : null, (flags & 4) != 0 ? buf.readUtf(2048) : null)); }
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record ProfileRequestPayload(UUID uuid, long token) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ProfileRequestPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "profile_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ProfileRequestPayload> CODEC = StreamCodec.ofMember(ProfileRequestPayload::write, ProfileRequestPayload::read);
        private static void write(ProfileRequestPayload payload, RegistryFriendlyByteBuf buf) { buf.writeUUID(payload.uuid()); buf.writeLong(payload.token()); }
        private static ProfileRequestPayload read(RegistryFriendlyByteBuf buf) { return new ProfileRequestPayload(buf.readUUID(), buf.readLong()); }
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record ProfileDataPayload(UUID uuid, long token, String signature, String avatar, String banner, long revision, int imageFields, String error) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ProfileDataPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "profile_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ProfileDataPayload> CODEC = StreamCodec.ofMember(ProfileDataPayload::write, ProfileDataPayload::read);
        private static void write(ProfileDataPayload payload, RegistryFriendlyByteBuf buf) { buf.writeUUID(payload.uuid()); buf.writeLong(payload.token());
            buf.writeUtf(payload.signature(), 256); buf.writeUtf(payload.avatar(), 2048); buf.writeUtf(payload.banner(), 2048);
            buf.writeLong(payload.revision()); buf.writeInt(payload.imageFields()); buf.writeUtf(payload.error(), 128); }
        private static ProfileDataPayload read(RegistryFriendlyByteBuf buf) { return new ProfileDataPayload(buf.readUUID(), buf.readLong(), buf.readUtf(256), buf.readUtf(2048),
                    buf.readUtf(2048), buf.readLong(), buf.readInt(), buf.readUtf(128)); }
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record ProfileChangedPayload(UUID uuid) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ProfileChangedPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "profile_changed"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ProfileChangedPayload> CODEC = StreamCodec.ofMember(ProfileChangedPayload::write, ProfileChangedPayload::read);
        private static void write(ProfileChangedPayload payload, RegistryFriendlyByteBuf buf) { buf.writeUUID(payload.uuid()); }
        private static ProfileChangedPayload read(RegistryFriendlyByteBuf buf) { return new ProfileChangedPayload(buf.readUUID()); }
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
    }
    /** 0 unknown, 1 member, 2 operator; existing payloads keep their wire format. */
    public record ProfileRolePayload(UUID uuid, long token, int state) implements CustomPacketPayload {
        public ProfileRolePayload {
            if (state < 0 || state > 2) throw new IllegalArgumentException("invalid_profile_role");
        }
        public static final CustomPacketPayload.Type<ProfileRolePayload> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "profile_role"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ProfileRolePayload> CODEC =
                StreamCodec.ofMember(ProfileRolePayload::write, ProfileRolePayload::read);
        private static void write(ProfileRolePayload payload, RegistryFriendlyByteBuf buf) {
            buf.writeUUID(payload.uuid());
            buf.writeLong(payload.token());
            buf.writeByte(payload.state());
        }
        private static ProfileRolePayload read(RegistryFriendlyByteBuf buf) {
            return new ProfileRolePayload(buf.readUUID(), buf.readLong(), buf.readUnsignedByte());
        }
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ProfileUpdatePayload.ID, ProfileUpdatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ProfileRequestPayload.ID, ProfileRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ProfileDataPayload.ID, ProfileDataPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ProfileChangedPayload.ID, ProfileChangedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ProfileRolePayload.ID, ProfileRolePayload.CODEC);
    }
}
