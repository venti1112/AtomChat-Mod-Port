package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;



public final class ReactionPayloads {
    private ReactionPayloads() {
    }

    
    public record ActionPayload(byte[] bytes) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ActionPayload> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "reaction_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC =
                StreamCodec.ofMember(ActionPayload::write, ActionPayload::read);

        private static void write(ActionPayload payload, RegistryFriendlyByteBuf buf) {
            buf.writeByteArray(payload.bytes());
        }

        private static ActionPayload read(RegistryFriendlyByteBuf buf) {
            return new ActionPayload(buf.readByteArray(128));
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    
    public record DataPayload(byte[] bytes) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DataPayload> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "reaction_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DataPayload> CODEC =
                StreamCodec.ofMember(DataPayload::write, DataPayload::read);

        private static void write(DataPayload payload, RegistryFriendlyByteBuf buf) {
            buf.writeByteArray(payload.bytes());
        }

        private static DataPayload read(RegistryFriendlyByteBuf buf) {
            return new DataPayload(buf.readByteArray(com.atom.chat.reaction.ReactionWire.MAX_BYTES));
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    
    public static void register() {
        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DataPayload.ID, DataPayload.CODEC);
    }
}
