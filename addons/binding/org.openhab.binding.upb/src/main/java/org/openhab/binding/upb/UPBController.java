package org.openhab.binding.upb;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.openhab.binding.upb.UPBDevice.DeviceState;
import org.openhab.binding.upb.handler.UPBIoHandler;
import org.openhab.binding.upb.handler.UPBThingHandler;
import org.openhab.binding.upb.internal.UPBMessage;
import org.openhab.binding.upb.internal.UPBMessage.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UPBController {
    private final Logger logger = LoggerFactory.getLogger(UPBController.class);
    private final UPBIoHandler ioHandler;
    private final ConcurrentHashMap<Byte, UPBDevice> devices = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Byte, UPBDevice> links = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Byte, UPBThingHandler> things = new ConcurrentHashMap<>();

    public UPBController(final UPBIoHandler ioHandler, final Map<String, String> config) {
        this.ioHandler = ioHandler;
    }

    public void incomingMessage(final UPBMessage msg) {
        if (msg.getType() != Type.MESSAGE_REPORT) {
            return;
        }

//        final UPBThingHandler src = devices.get(message.getSource());
        final UPBDevice dst;
        final byte dstId;
        if (isValidId(msg.getDestination())) {
            dstId = msg.getDestination();
        } else {
            dstId = msg.getSource();
        }
        if (msg.getControlWord().isLink()) {
            dst = links.get(dstId);
            if (dst == null) {
                logger.debug("Received message for unknown link with id {}: {}", dstId & 0xff, msg.getCommand());
                return;
            }
        } else {
            dst = devices.getOrDefault(dstId, new UPBDevice(dstId));
            if (dstId == msg.getSource()) {
                dst.setState(DeviceState.ALIVE);
            }
            final UPBThingHandler thingHnd = things.get(dstId);
            if (thingHnd == null) {
                logger.debug("Received message for unknown device with id {}: {}", dstId & 0xff, msg.getCommand());
                return;
            }
            logger.info("Received message for {}: {}", dst, msg.getCommand());
            thingHnd.onMessageReceived(msg);
        }
    }

    private boolean isValidId(byte id) {
        return id != 0 && id != -1;
    }

    public UPBDevice getDevice(final byte unitId) {
        return devices.get(unitId);
    }

    public void deviceAdded(@NonNull final ThingHandler childHandler, @NonNull final Thing childThing) {
        if (childHandler instanceof UPBThingHandler) {
            final UPBThingHandler hnd = (UPBThingHandler) childHandler;
            things.put(hnd.getUnitId(), hnd);
        }
    }

    public void deviceRemoved(@NonNull final ThingHandler childHandler, @NonNull final Thing childThing) {
        if (childHandler instanceof UPBThingHandler) {
            final UPBThingHandler hnd = (UPBThingHandler) childHandler;
            things.remove(hnd.getUnitId(), hnd);
        }
    }
}
