package org.openhab.binding.upb.handler;

import java.util.concurrent.CompletionStage;

import org.openhab.binding.upb.internal.MessageBuilder;

public interface UPBIoHandler {
    enum CmdStatus {
        ACK,
        NAK,
        WRITE_FAILED
    }

    void deviceDiscovered(int node);

    CompletionStage<CmdStatus> sendPacket(MessageBuilder message);
}
