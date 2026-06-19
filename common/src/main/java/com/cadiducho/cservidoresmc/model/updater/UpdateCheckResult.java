package com.cadiducho.cservidoresmc.model.updater;

import lombok.Getter;

@Getter
public class UpdateCheckResult {

    private final UpdateCheckStatus status;
    private final String installedVersion;
    private final String availableVersion;
    private final String description;
    private final String downloadUrl;
    private final String message;
    private final long checkedAtMillis;

    private UpdateCheckResult(UpdateCheckStatus status, String installedVersion, String availableVersion,
                              String description, String downloadUrl, String message, long checkedAtMillis) {
        this.status = status;
        this.installedVersion = installedVersion;
        this.availableVersion = availableVersion;
        this.description = description;
        this.downloadUrl = downloadUrl;
        this.message = message;
        this.checkedAtMillis = checkedAtMillis;
    }

    public static UpdateCheckResult pending(String installedVersion) {
        return new UpdateCheckResult(UpdateCheckStatus.PENDING, installedVersion, "", "", "", "", 0L);
    }

    public static UpdateCheckResult upToDate(String installedVersion, String availableVersion) {
        return new UpdateCheckResult(UpdateCheckStatus.UP_TO_DATE, installedVersion, availableVersion, "", "", "", System.currentTimeMillis());
    }

    public static UpdateCheckResult updateAvailable(String installedVersion, String availableVersion, String description, String downloadUrl) {
        return new UpdateCheckResult(UpdateCheckStatus.UPDATE_AVAILABLE, installedVersion, availableVersion, description, downloadUrl, "", System.currentTimeMillis());
    }

    public static UpdateCheckResult error(String installedVersion, String message) {
        return new UpdateCheckResult(UpdateCheckStatus.ERROR, installedVersion, "", "", "", message, System.currentTimeMillis());
    }

    public boolean isUpdateAvailable() {
        return status == UpdateCheckStatus.UPDATE_AVAILABLE;
    }
}
