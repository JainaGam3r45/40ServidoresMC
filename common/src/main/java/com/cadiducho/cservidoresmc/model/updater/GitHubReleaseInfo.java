package com.cadiducho.cservidoresmc.model.updater;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
public class GitHubReleaseInfo {

    @SerializedName("tag_name")
    private String tagName;

    @SerializedName("html_url")
    private String htmlUrl;

    private String name;
    private String body;

    public String getVersion() {
        if (tagName == null) {
            return "";
        }

        return tagName.startsWith("v") || tagName.startsWith("V") ? tagName.substring(1) : tagName;
    }

    public String getDescription() {
        if (body != null && !body.trim().isEmpty()) {
            String[] lines = body.split("\\r?\\n");
            for (String line : lines) {
                String cleanLine = line.replace("#", "").replace("*", "").trim();
                if (!cleanLine.isEmpty()) {
                    return cleanLine;
                }
            }
        }

        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }

        return "Ver cambios en GitHub";
    }
}
