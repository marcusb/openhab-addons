package org.openhab.binding.upb;

import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * Common constants used in the binding.
 */
public final class Constants {
    public static final String BINDING_ID = "upb";
    public static final ThingTypeUID PIM_UID = new ThingTypeUID(BINDING_ID, "serial-pim");
    public static final ThingTypeUID UPB_THING_UID = new ThingTypeUID(BINDING_ID, "device");
    public static final ThingTypeUID LEVITON_38A00_1_UID = new ThingTypeUID(BINDING_ID, "leviton-38a00-1");
    public static final String SWITCH_CHANNEL_ID = "switch-channel";

    public static final String CONFIGURATION_PORT = "port";
    public static final String CONFIGURATION_UNIT_ID = "unitId";
    public static final String CONFIGURATION_NETWORK_ID = "networkId";

    public static final String OFFLINE_CTLR_OFFLINE = "@text/upb.thingstate.controller_offline";
    public static final String OFFLINE_NODE_DEAD = "@text/upb.thingstate.node_dead";
    public static final String OFFLINE_NODE_NOTFOUND = "@text/upb.thingstate.node_notfound";
    public static final String OFFLINE_SERIAL_EXISTS = "@text/upb.thingstate.serial_notfound";
    public static final String OFFLINE_SERIAL_INUSE = "@text/upb.thingstate.serial_inuse";
    public static final String OFFLINE_SERIAL_UNSUPPORTED = "@text/upb.thingstate.serial_unsupported";
    public static final String OFFLINE_SERIAL_LISTENERS = "@text/upb.thingstate.serial_listeners";

    private Constants() {
        // static class
    }
}
