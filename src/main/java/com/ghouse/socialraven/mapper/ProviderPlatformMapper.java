package com.ghouse.socialraven.mapper;

import com.ghouse.socialraven.constant.Platform;
import com.ghouse.socialraven.constant.Provider;

public class ProviderPlatformMapper {

    public static Platform getPlatformByProvider(Provider provider) {
        if (provider == null) {
            return null;
        }

        if (Provider.YOUTUBE == provider) {
            return Platform.youtube;
        }

        if (Provider.X == provider) {
            return Platform.x;
        }

        if (Provider.LINKEDIN == provider) {
            return Platform.linkedin;
        }

        if (Provider.INSTAGRAM == provider) {
            return Platform.instagram;
        }

        if (Provider.FACEBOOK == provider) {
            return Platform.facebook;
        }

        return null;
    }

    public static Provider getProviderByPlatform(Platform platform) {
        if (platform == null) {
            return null;
        }

        if (Platform.youtube == platform) {
            return Provider.YOUTUBE;
        }

        if (Platform.x == platform) {
            return Provider.X;
        }

        if (Platform.linkedin == platform) {
            return Provider.LINKEDIN;
        }

        if (Platform.instagram == platform) {
            return Provider.INSTAGRAM;
        }

        if (Platform.facebook == platform) {
            return Provider.FACEBOOK;
        }

        return null;
    }
}
