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

import java.time.Instant;
import java.util.Objects;

import com.google.gson.annotations.SerializedName;

/**
 * Token response from the Bluelink authentication API.
 *
 * @author Marcus Better - Initial contribution
 */
public final class TokenResponse {
    @SerializedName("access_token")
    private final String accessToken;

    @SerializedName("refresh_token")
    private final String refreshToken;

    @SerializedName("expires_in")
    private final String expiresIn;

    private transient final Instant createdAt = Instant.now();

    public TokenResponse(final String accessToken, final String refreshToken, final String expiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
    }

    public Instant validUntil() {
        if (createdAt == null || expiresIn == null) {
            return Instant.EPOCH;
        }
        return createdAt.plusSeconds(Integer.parseInt(expiresIn) - 60);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(validUntil());
    }

    @SerializedName("access_token")
    public String accessToken() {
        return accessToken;
    }

    @SerializedName("refresh_token")
    public String refreshToken() {
        return refreshToken;
    }

    @SerializedName("expires_in")
    public String expiresIn() {
        return expiresIn;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        final var that = (TokenResponse) obj;
        return Objects.equals(this.accessToken, that.accessToken)
                && Objects.equals(this.refreshToken, that.refreshToken)
                && Objects.equals(this.expiresIn, that.expiresIn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accessToken, refreshToken, expiresIn);
    }
}
