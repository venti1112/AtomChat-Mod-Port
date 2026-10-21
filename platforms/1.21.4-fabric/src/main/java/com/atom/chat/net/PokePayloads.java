package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/**
 * Poke-companion payloads. Same optional-channel shape as the avatar
 * companion: registered on both logical sides, only sent once the server has
 * negotiated the channel, so a vanilla or companion-less server never receives
 * an unknown payload and the client silently degrades to a local-only poke.
 */
public final class PokePayloads {
    private PokePayloads() {
    }

    /** C2S: "I poked the player with this uuid." The server resolves the sender. */
    public record PokeC2SPayload(UUID target) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<PokeC2SPayload> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "poke"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PokeC2SPayload> CODEC =
                StreamCodec.ofMember(PokeC2SPayload::write, PokeC2SPayload::read);

        private static void write(PokeC2SPayload payload, RegistryFriendlyByteBuf buf) {
            buf.writeUUID(payload.target());
        }

        private static PokeC2SPayload read(RegistryFriendlyByteBuf buf) {
            return new PokeC2SPayload(buf.readUUID());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** S2C: "this player poked you." */
    public record PokeS2CPayload(UUID from) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<PokeS2CPayload> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "poked"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PokeS2CPayload> CODEC =
                StreamCodec.ofMember(PokeS2CPayload::write, PokeS2CPayload::read);

        private static void write(PokeS2CPayload payload, RegistryFriendlyByteBuf buf) {
            buf.writeUUID(payload.from());
        }

        private static PokeS2CPayload read(RegistryFriendlyByteBuf buf) {
            return new PokeS2CPayload(buf.readUUID());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Registers both codecs; must run on both logical sides (common init). */
    public static void register() {
        PayloadTypeRegistry.playC2S().register(PokeC2SPayload.ID, PokeC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PokeS2CPayload.ID, PokeS2CPayload.CODEC);
    }
}
