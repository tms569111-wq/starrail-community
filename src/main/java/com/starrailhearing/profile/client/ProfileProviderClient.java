package com.starrailhearing.profile.client;

import com.starrailhearing.character.domain.ProfileProvider;

public interface ProfileProviderClient {
    ProfileProvider provider();

    PublicGameProfile fetch(String uid, boolean forceUpdate);
}
