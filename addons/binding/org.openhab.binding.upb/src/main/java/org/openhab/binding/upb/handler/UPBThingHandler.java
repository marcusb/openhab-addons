package org.openhab.binding.upb.handler;

import java.math.BigDecimal;

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.upb.Constants;
import org.openhab.binding.upb.UPBDevice;
import org.openhab.binding.upb.handler.UPBIoHandler.CmdStatus;
import org.openhab.binding.upb.internal.MessageBuilder;
import org.openhab.binding.upb.internal.UPBMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UPBThingHandler extends BaseThingHandler {
    private final Logger logger = LoggerFactory.getLogger(UPBThingHandler.class);

    private volatile PIMHandler controllerHandler;
    private volatile byte network;
    private volatile int unitId;

    public UPBThingHandler(final Thing device) {
        super(device);
    }

    @Override
    public void initialize() {
        logger.debug("initializing UPB thing handler {}", getThing().getUID());

        final BigDecimal networkId = (BigDecimal) getConfig().get(Constants.CONFIGURATION_NETWORK_ID);
        if (networkId == null) {
            logger.error("Network ID is not set in {}", getThing().getUID());
            return;
        }
        if (networkId.compareTo(BigDecimal.ZERO) < 0 || networkId.compareTo(BigDecimal.valueOf(255)) > 0) {
            logger.error("Network ID ({}) out of range for {}", networkId, getThing().getUID());
            return;
        }
        network = networkId.byteValue();

        final BigDecimal cfgUnitId = (BigDecimal) getConfig().get(Constants.CONFIGURATION_UNIT_ID);
        if (cfgUnitId == null) {
            logger.error("Unit ID is not set in {}", getThing().getUID());
            return;
        }
        unitId = cfgUnitId.intValue();
        if (unitId < 1 || unitId > 250) {
            logger.error("Unit ID ({}) out of range for {}", cfgUnitId, getThing().getUID());
            return;
        }

        final Bridge bridge = getBridge();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, Constants.OFFLINE_CTLR_OFFLINE);
            return;
        }
        bridgeStatusChanged(bridge.getStatusInfo());
    }

    @Override
    public void dispose() {

    }

    @Override
    public void bridgeStatusChanged(final ThingStatusInfo bridgeStatusInfo) {
        logger.debug("DEV {}: Controller status is {}", unitId, bridgeStatusInfo.getStatus());

        if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, Constants.OFFLINE_CTLR_OFFLINE);
            return;
        }

        logger.debug("DEV {}: Controller is ONLINE. Starting device initialisation.", unitId);

        final Bridge bridge = getBridge();
        if (bridge == null) {
            logger.debug("DEV {}: bridge is null!", unitId);
            return;
        }
        final PIMHandler bridgeHandler = (PIMHandler) bridge.getHandler();
        if (bridgeHandler == null) {
            logger.debug("DEV {}: bridge handler is null!", unitId);
            return;
        }
        updateDeviceStatus(bridgeHandler);

        // If we already know the controller, then we don't want to initialise again
        if (controllerHandler != null) {
            logger.debug("DEV {}: Controller already initialised", unitId);
        } else {
            controllerHandler = bridgeHandler;
        }
        pingDevice();
    }

    @Override
    public void handleCommand(final ChannelUID channelUID, final Command cmd) {
        if (controllerHandler == null) {
            logger.warn("DEV {}: received cmd {} but no bridge handler", unitId, cmd);
            return;
        }

        final byte[] cmdBytes;
        if (cmd == OnOffType.ON) {
            cmdBytes = new byte[] { UPBMessage.Command.ACTIVATE.toByte() };
        } else if (cmd == OnOffType.OFF) {
            cmdBytes = new byte[] { UPBMessage.Command.DEACTIVATE.toByte() };
        } else if (cmd instanceof PercentType) {
            cmdBytes = new byte[] { UPBMessage.Command.GOTO.toByte(), ((PercentType) cmd).byteValue() };
        } else if (cmd == RefreshType.REFRESH) {
            refreshDeviceState();
            return;
        } else {
            logger.warn("DEV {}: unsupported cmd {}", unitId, cmd);
            return;
        }

        final boolean isLink = channelUID.
        final MessageBuilder message = MessageBuilder.create().network(network).destination((byte) unitId).link(isLink)
                .command(cmdBytes);

        controllerHandler.sendPacket(message).thenAccept(this::updateStatus);
    }

    public byte getUnitId() {
        return (byte) unitId;
    }

    public void onMessageReceived(final UPBMessage msg) {
        updateStatus(ThingStatus.ONLINE);
        final State state;
        switch (msg.getCommand()) {
            case ACTIVATE:
                state = OnOffType.ON;
                break;

            case DEACTIVATE:
                state = OnOffType.OFF;
                break;

            case GOTO:
                if (msg.getArguments().length == 0) {
                    logger.warn("DEV {}: malformed GOTO cmd", unitId);
                    return;
                }
                final int level = msg.getArguments()[0];
                if (level == 100) {
                    state = OnOffType.ON;
                } else {
                    state = OnOffType.OFF;
                }
                break;

            default:
                logger.debug("DEV {}: Message {} ignored", unitId, msg.getCommand());
                return;
        }
        updateState(Constants.SWITCH_CHANNEL_ID, state);
    }

    private void updateDeviceStatus(final PIMHandler bridgeHandler) {
        final UPBDevice device = bridgeHandler.getDevice(getUnitId());
        if (device == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, Constants.OFFLINE_NODE_NOTFOUND);
        } else {
            switch (device.getState()) {
                case INITIALIZING:
                case ALIVE:
                    updateStatus(ThingStatus.ONLINE);
                    break;
                case DEAD:
                case FAILED:
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            Constants.OFFLINE_NODE_DEAD);
                    break;
            }
        }
    }

    private void pingDevice() {
        controllerHandler.sendPacket(MessageBuilder.create().ackMessage(true).network(network)
                .destination((byte) unitId).command((byte) 0x00)).thenAccept(this::updateStatus);
    }

    private void updateStatus(final CmdStatus result) {
        switch (result) {
            case WRITE_FAILED:
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, Constants.OFFLINE_NODE_DEAD);
                break;

            case ACK:
            case NAK:
                updateStatus(ThingStatus.ONLINE);
                break;
        }
    }

    private void refreshDeviceState() {
        controllerHandler
                .sendPacket(MessageBuilder.create().network(network).destination((byte) unitId).command((byte) 0x30))
                .thenAccept(this::updateStatus);
    }
}
