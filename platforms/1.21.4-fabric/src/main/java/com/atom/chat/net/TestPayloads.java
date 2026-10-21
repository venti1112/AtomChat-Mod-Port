package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/**
 * Payload behind the {@code /atomchat test} command: the server tells the client
 * to behave as if it had just seen something only a real session can produce.
 *
 * <p>Both simulated events are client-side reactions — a notification banner and
 * a received poke — so the wire only carries which reaction plus the few fields
 * the renderer would otherwise have read off a captured message. The sender's
 * real UUID travels along so a test banner shows that player's actual face
 * rather than a placeholder.</p>
 *
 * <p>Optional channel, like the other companions: the server sends only when the
 * client negotiated it, so a vanilla client never sees an unknown payload.</p>
 */
public final class TestPayloads {
    private TestPayloads() {
    }

    public static final String KIND_MENTION = "mention";
    public static final String KIND_QUOTE = "quote";
    public static final String KIND_WHISPER = "whisper";
    /** Not a banner: the receiver plays a poke cue and asks the open panel to react. */
    public static final String KIND_POKE = "poke";

    /**
     * S2C: "as if you had just received this". Empty {@code text} or
     * {@code senderName} mean "use the client's own default" — the text is then
     * resolved client-side, so it lands in the client's language rather than the
     * server's.
     */
    public record TestEventS2CPayload(String kind, UUID senderUuid, String senderName, String text)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TestEventS2CPayload> ID =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "test_event"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TestEventS2CPayload> CODEC =
                StreamCodec.ofMember(TestEventS2CPayload::write, TestEventS2CPayload::read);

        public TestEventS2CPayload {
            kind = kind == null ? "" : kind;
            senderName = senderName == null ? "" : senderName;
            text = text == null ? "" : text;
        }

        private static void write(TestEventS2CPayload payload, RegistryFriendlyByteBuf buf) {
            buf.writeUtf(payload.kind());
            buf.writeUUID(payload.senderUuid());
            buf.writeUtf(payload.senderName());
            buf.writeUtf(payload.text());
        }

        private static TestEventS2CPayload read(RegistryFriendlyByteBuf buf) {
            return new TestEventS2CPayload(buf.readUtf(), buf.readUUID(),
                    buf.readUtf(), buf.readUtf());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Registers the S2C codec; runs on both logical sides (common init). */
    public static void register() {
        PayloadTypeRegistry.playS2C().register(TestEventS2CPayload.ID, TestEventS2CPayload.CODEC);
    }
}
