package com.cellmachine.generator;

public enum SimulationOutputFormat {
    GIF("gif", "image/gif"),
    MP4("mp4", "video/mp4");

    private final String extension;
    private final String mediaType;

    SimulationOutputFormat(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String fileExtension() {
        return extension;
    }

    public String mediaType() {
        return mediaType;
    }
}
