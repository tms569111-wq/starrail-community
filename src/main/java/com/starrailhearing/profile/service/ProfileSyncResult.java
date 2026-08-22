package com.starrailhearing.profile.service;

public record ProfileSyncResult(
        int seen,
        int added,
        int upgraded,
        int ignored
) {
}
