package com.cellmachine.generator;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

public final class GifWriter implements Closeable {
    private final int width;
    private final int height;
    private final int scale;
    private final int delayCs;
    private final ImageWriter writer;
    private final ImageWriteParam writeParam;
    private final ImageOutputStream outputStream;
    private final IndexColorModel colorModel;
    private boolean firstFrame = true;
    private boolean closed;

    public GifWriter(OutputStream output, int width, int height, int scale, int delayCs, Palette2D palette) throws IOException {
        if (scale <= 0) {
            throw new IllegalArgumentException("Scale must be greater than zero");
        }
        Objects.requireNonNull(palette, "palette");
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.delayCs = delayCs;
        var writers = ImageIO.getImageWritersBySuffix("gif");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No GIF ImageWriter found on classpath");
        }
        this.writer = writers.next();
        this.writeParam = writer.getDefaultWriteParam();
        this.outputStream = ImageIO.createImageOutputStream(output);
        this.writer.setOutput(outputStream);
        this.writer.prepareWriteSequence(null);
        this.colorModel = buildColorModel(palette.deadColor, palette.aliveColor);
    }

    public void writeFrame(Grid grid) throws IOException {
        ensureDimensions(grid);
        BufferedImage image = rasterize(grid);
        IIOMetadata metadata = buildMetadata(image, firstFrame);
        writer.writeToSequence(new IIOImage(image, null, metadata), writeParam);
        firstFrame = false;
    }

    private void ensureDimensions(Grid grid) {
        if (grid.width() != width || grid.height() != height) {
            throw new IllegalArgumentException("Grid dimensions do not match writer dimensions");
        }
    }

    private BufferedImage rasterize(Grid grid) {
        int scaledWidth = width * scale;
        int scaledHeight = height * scale;
        BufferedImage image = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_BYTE_INDEXED, colorModel);
        for (int y = 0; y < grid.height(); y++) {
            for (int x = 0; x < grid.width(); x++) {
                if (!grid.get(x, y)) {
                    continue;
                }
                for (int sy = 0; sy < scale; sy++) {
                    int row = (y * scale + sy) * scaledWidth;
                    for (int sx = 0; sx < scale; sx++) {
                        int pixelX = x * scale + sx;
                        image.getRaster().setSample(pixelX, y * scale + sy, 0, 1);
                    }
                }
            }
        }
        return image;
    }

    private IIOMetadata buildMetadata(BufferedImage image, boolean includeLoop) throws IOException {
        ImageTypeSpecifier typeSpecifier = ImageTypeSpecifier.createFromRenderedImage(image);
        IIOMetadata metadata = writer.getDefaultImageMetadata(typeSpecifier, writeParam);
        String formatName = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(formatName);

        IIOMetadataNode graphicsControl = getNode(root, "GraphicControlExtension");
        graphicsControl.setAttribute("disposalMethod", "none");
        graphicsControl.setAttribute("userInputFlag", "FALSE");
        graphicsControl.setAttribute("transparentColorFlag", "FALSE");
        graphicsControl.setAttribute("delayTime", Integer.toString(delayCs));
        graphicsControl.setAttribute("transparentColorIndex", "0");

        IIOMetadataNode imageDescriptor = getNode(root, "ImageDescriptor");
        imageDescriptor.setAttribute("imageLeftPosition", "0");
        imageDescriptor.setAttribute("imageTopPosition", "0");
        imageDescriptor.setAttribute("imageWidth", Integer.toString(image.getWidth()));
        imageDescriptor.setAttribute("imageHeight", Integer.toString(image.getHeight()));
        imageDescriptor.setAttribute("interlaceFlag", "FALSE");

        if (includeLoop) {
            IIOMetadataNode appExtensions = getNode(root, "ApplicationExtensions");
            IIOMetadataNode appExtension = new IIOMetadataNode("ApplicationExtension");
            appExtension.setAttribute("applicationID", "NETSCAPE");
            appExtension.setAttribute("authenticationCode", "2.0");
            appExtension.setUserObject(new byte[]{1, 0, 0});
            appExtensions.appendChild(appExtension);
        }

        applyLocalColorTable(root, image);
        metadata.setFromTree(formatName, root);
        return metadata;
    }

    private IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
        for (int i = 0; i < rootNode.getLength(); i++) {
            if (nodeName.equals(rootNode.item(i).getNodeName())) {
                return (IIOMetadataNode) rootNode.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(nodeName);
        rootNode.appendChild(node);
        return node;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        writer.endWriteSequence();
        outputStream.close();
    }

    private void applyLocalColorTable(IIOMetadataNode root, BufferedImage image) {
        if (!(image.getColorModel() instanceof IndexColorModel indexModel)) {
            throw new IllegalStateException("Expected indexed color model for GIF rendering");
        }
        IIOMetadataNode localColorTable = getNode(root, "LocalColorTable");
        clearChildren(localColorTable);
        int size = indexModel.getMapSize();
        localColorTable.setAttribute("sizeOfLocalColorTable", Integer.toString(size));
        localColorTable.setAttribute("sortFlag", "FALSE");
        for (int i = 0; i < size; i++) {
            IIOMetadataNode entry = new IIOMetadataNode("ColorTableEntry");
            entry.setAttribute("index", Integer.toString(i));
            entry.setAttribute("red", Integer.toString(indexModel.getRed(i)));
            entry.setAttribute("green", Integer.toString(indexModel.getGreen(i)));
            entry.setAttribute("blue", Integer.toString(indexModel.getBlue(i)));
            localColorTable.appendChild(entry);
        }
    }

    private void clearChildren(IIOMetadataNode node) {
        while (node.hasChildNodes()) {
            node.removeChild(node.getFirstChild());
        }
    }

    private IndexColorModel buildColorModel(String deadHex, String aliveHex) {
        int deadRgb = parseHexColor(deadHex);
        int aliveRgb = parseHexColor(aliveHex);
        byte[] reds = new byte[]{
            (byte) ((deadRgb >> 16) & 0xFF),
            (byte) ((aliveRgb >> 16) & 0xFF)
        };
        byte[] greens = new byte[]{
            (byte) ((deadRgb >> 8) & 0xFF),
            (byte) ((aliveRgb >> 8) & 0xFF)
        };
        byte[] blues = new byte[]{
            (byte) (deadRgb & 0xFF),
            (byte) (aliveRgb & 0xFF)
        };
        return new IndexColorModel(1, 2, reds, greens, blues);
    }

    private int parseHexColor(String hex) {
        String normalized = hex.startsWith("#") ? hex.substring(1) : hex;
        if (normalized.length() != 6) {
            throw new IllegalArgumentException("Expected RGB hex color in format RRGGBB: " + hex);
        }
        return Integer.parseInt(normalized, 16);
    }
}
