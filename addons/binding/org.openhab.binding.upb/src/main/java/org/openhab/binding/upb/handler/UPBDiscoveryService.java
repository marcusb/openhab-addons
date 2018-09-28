package org.openhab.binding.upb.handler;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UPBDiscoveryService extends AbstractDiscoveryService {
    private final Logger logger = LoggerFactory.getLogger(UPBDiscoveryService.class);
    private final PIMHandler pimHandler;

    public UPBDiscoveryService(final PIMHandler pimHandler, final int searchTime) {
        super(searchTime);
        this.pimHandler = pimHandler;
    }

    @Override
    protected void startScan() {
        logger.debug("UPB discovery start {}", pimHandler.getThing().getUID());
    }
}
