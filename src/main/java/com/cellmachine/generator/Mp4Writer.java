package com.cellmachine.generator;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jcodec.api.awt.AWTSequenceEncoder;

/**
 * Writes an MP4 animation (H.264 baseline) from simulation frames.
 */
public final class Mp4Writer implements Closeable {

    private static final Palette2D PALETTE = Palette2D.ysConcreteJungle;

    private final int width;
    private final int height;
    private final int scale;
    private final Path tempFile;
    private final AWTSequenceEncoder encoder;
    private final Color deadColor;
    private final Color aliveColor;
    private boolean closed;
    private boolean consumed;

    public Mp4Writer(int width, int height, int scale, int delayCs) throws IOException {
        if (scale <= 0) {
            throw new IllegalArgumentException("Scale must be greater than zero");
        }
        if (delayCs <= 0) {
            throw new IllegalArgumentException("Delay must be positive");
        }
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.tempFile = Files.createTempFile("cell-machine-", ".mp4");
        double fpsValue = 100.0 / delayCs;
        int fps = (int) Math.max(1, Math.round(fpsValue));
        this.encoder = AWTSequenceEncoder.createSequenceEncoder(tempFile.toFile(), fps);
        this.deadColor = decodeColor(PALETTE.deadColor);
        this.aliveColor = decodeColor(PALETTE.aliveColor);
    }

    public void writeFrame(Grid grid) throws IOException {
        ensureDimensions(grid);
        encoder.encodeImage(rasterize(grid));
    }

    public byte[] toByteArray() throws IOException {
        if (!closed) {
            throw new IllegalStateException("Writer must be closed before reading bytes");
        }
        if (consumed) {
            throw new IllegalStateException("MP4 bytes already consumed");
        }
        byte[] bytes = Files.readAllBytes(tempFile);
        Files.deleteIfExists(tempFile);
        consumed = true;
        return bytes;
    }

    private void ensureDimensions(Grid grid) {
        if (grid.width() != width || grid.height() != height) {
            throw new IllegalArgumentException("Grid dimensions do not match writer dimensions");
        }
    }

    private BufferedImage rasterize(Grid grid) {
        int scaledWidth = width * scale;
        int scaledHeight = height * scale;
        int paddedWidth = alignToMacroblock(scaledWidth);
        int paddedHeight = alignToMacroblock(scaledHeight);
        BufferedImage image = new BufferedImage(paddedWidth, paddedHeight, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(deadColor);
            graphics.fillRect(0, 0, paddedWidth, paddedHeight);
            graphics.setColor(aliveColor);
            for (int y = 0; y < grid.height(); y++) {
                for (int x = 0; x < grid.width(); x++) {
                    if (!grid.get(x, y)) {
                        continue;
                    }
                    graphics.fillRect(x * scale, y * scale, scale, scale);
                }
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private int alignToMacroblock(int value) {
        int block = 16;
        return ((value + block - 1) / block) * block;
    }

    private Color decodeColor(String hex) {
        String normalized = hex.startsWith("#") ? hex.substring(1) : hex;
        if (normalized.length() != 6) {
            throw new IllegalArgumentException("Expected RGB hex color in format RRGGBB: " + hex);
        }
        int rgb = Integer.parseInt(normalized, 16);
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        encoder.finish();
    }
}
