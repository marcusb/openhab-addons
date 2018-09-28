package org.openhab.binding.upb;

import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;

public class UPBThingChannel {
    public enum DataType {
        OnOffType,
        PercentType
    }

    private final ChannelUID uid;
    private final ChannelTypeUID channelTypeUID;
    private final DataType dataType;

    public UPBThingChannel(final ChannelUID uid, final ChannelTypeUID channelTypeUID, final DataType dataType) {
        this.uid = uid;
        this.channelTypeUID = channelTypeUID;
        this.dataType = dataType;
    }

    public ChannelUID getUid() {
        return uid;
    }

    public ChannelTypeUID getChannelTypeUID() {
        return channelTypeUID;
    }

    public DataType getDataType() {
        return dataType;
    }
}
