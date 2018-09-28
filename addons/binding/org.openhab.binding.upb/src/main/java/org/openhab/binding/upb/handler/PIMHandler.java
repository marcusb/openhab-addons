package org.openhab.binding.upb.handler;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.upb.Constants;
import org.openhab.binding.upb.UPBController;
import org.openhab.binding.upb.UPBDevice;
import org.openhab.binding.upb.internal.UPBMessage;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PIMHandler extends BaseBridgeHandler implements UPBIoHandler {
    private static final int DISCOVERY_TIMEOUT = 30;

    private final Logger logger = LoggerFactory.getLogger(PIMHandler.class);

    private byte network = 0;
    private DiscoveryService discoveryService;
    private ServiceRegistration discoveryRegistration;
    private volatile UPBController controller;

    public PIMHandler(final Bridge bridge) {
        super(bridge);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing UPB PIM {}.", getThing().getUID());
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, Constants.OFFLINE_CTLR_OFFLINE);
        Map<String, String> config = new HashMap<>();
//        config.put("masterController", isMaster.toString());
//        config.put("sucNode", sucNode.toString());
//        config.put("secureInclusion", secureInclusionMode.toString());
//        config.put("networkKey", networkKey);
//        config.put("wakeupDefaultPeriod", wakeupDefaultPeriod.toString());

        // TODO: Handle soft reset?
        controller = new UPBController(this, config);

        // Start the discovery service
        discoveryService = new UPBDiscoveryService(this, DISCOVERY_TIMEOUT);
//        discoveryService.activate();

        // And register it as an OSGi service
        discoveryRegistration = bundleContext.registerService(DiscoveryService.class.getName(), discoveryService,
                new Hashtable<String, Object>());
    }

    @Override
    public void dispose() {
        logger.info("UPB binding shutting down...");
        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // TODO Auto-generated method stub
    }

    @Override
    public void deviceDiscovered(int node) {
        // TODO Auto-generated method stub

    }

    @Override
    public void childHandlerInitialized(final ThingHandler childHandler, final Thing childThing) {
        logger.debug("child handler initialized: {}", childThing.getUID());
        controller.deviceAdded(childHandler, childThing);
        super.childHandlerInitialized(childHandler, childThing);
    }

    @Override
    public void childHandlerDisposed(final ThingHandler childHandler, final Thing childThing) {
        logger.debug("child handler disposed: {}", childThing.getUID());
        controller.deviceRemoved(childHandler, childThing);
        super.childHandlerDisposed(childHandler, childThing);
    }

    protected void incomingMessage(final UPBMessage msg) {
        if (controller == null) {
            return;
        }
        controller.incomingMessage(msg);
    }

    public UPBDevice getDevice(byte unitId) {
        if (controller == null) {
            return null;
        }
        return controller.getDevice(unitId);
    }
}
