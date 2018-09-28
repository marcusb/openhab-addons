package org.openhab.binding.upb;

public class UPBDevice {
    private final int unitId;

    private DeviceState state;

    public enum DeviceState {
        INITIALIZING,
        ALIVE,
        DEAD,
        FAILED
    }

    public UPBDevice(final int unitId) {
        this.unitId = unitId;
        setState(DeviceState.INITIALIZING);
    }

    public int getUnitId() {
        return unitId;
    }

    public DeviceState getState() {
        return state;
    }

    public void setState(final DeviceState state) {
        this.state = state;
    }
}
