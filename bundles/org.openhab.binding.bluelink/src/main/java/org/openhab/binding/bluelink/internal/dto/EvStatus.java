/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.bluelink.internal.dto;

import java.util.List;

import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

import com.google.gson.annotations.SerializedName;

/**
 * EV-specific status.
 *
 * @author Marcus Better - Initial contribution
 */
public record EvStatus(boolean batteryCharge, int batteryStatus, int batteryPlugin, ReserveChargeInfo reservChargeInfos,
        List<DrivingDistance> drvDistance, ChargeRemainingTime remainTime2) {

    public record ReserveChargeInfo(@SerializedName("targetSOCList") List<TargetSOC> targetSocList) {
        /**
         * Target state of charge setting.
         */
        public record TargetSOC(int plugType, // 0 = DC, 1 = AC
                @SerializedName("targetSOCLevel") int targetSocLevel) {
        }
    }

    public record DrivingDistance(RangeByFuel rangeByFuel) {

        /**
         * Range by fuel type.
         */
        public record RangeByFuel(RangeValue totalAvailableRange, RangeValue evModeRange, RangeValue gasModeRange) {

            /**
             * Range value with unit.
             */
            public record RangeValue(double value, int unit) {

                public State getRange() {
                    return switch (unit) {
                        case 1 -> new QuantityType<>(value * 1000, SIUnits.METRE);
                        case 2, 3 -> new QuantityType<>(value, ImperialUnits.MILE);
                        default -> UnDefType.UNDEF;
                    };
                }
            }
        }
    }

    public record ChargeRemainingTime(
            // Current
            TimeValue atc,
            // Fast
            TimeValue etc1,
            // Portable
            TimeValue etc2,
            // Station
            TimeValue etc3) {

        public record TimeValue(int value, int unit) {
        }
    }
}
