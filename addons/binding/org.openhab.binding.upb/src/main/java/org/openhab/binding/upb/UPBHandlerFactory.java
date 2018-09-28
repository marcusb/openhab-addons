package org.openhab.binding.upb;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.openhab.binding.upb.handler.SerialPIMHandler;
import org.openhab.binding.upb.handler.UPBThingHandler;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true, service = { ThingHandlerFactory.class })
public class UPBHandlerFactory extends BaseThingHandlerFactory {
    private final Logger logger = LoggerFactory.getLogger(UPBHandlerFactory.class);

    @Override
    public boolean supportsThingType(final ThingTypeUID thingTypeUID) {
        return Constants.BINDING_ID.equals(thingTypeUID.getBindingId());
    }

    @Override
    protected @Nullable ThingHandler createHandler(final Thing thing) {
        logger.debug("Creating thing {}", thing.getUID());
        final ThingTypeUID thingTypeUID = thing.getThingTypeUID();
        if (thingTypeUID.equals(Constants.PIM_UID)) {
            return new SerialPIMHandler((Bridge) thing);
        }
        return new UPBThingHandler(thing);
    }
}
