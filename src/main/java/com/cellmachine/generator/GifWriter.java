package com.cellmachine.generator;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

public final class GifWriter implements Closeable {

    private static final byte[] BLACK_WHITE = new byte[]{0x00, (byte) 0xFF};

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

    public GifWriter(OutputStream output, int width, int height, int scale, int delayCs) throws IOException {
        if (scale <= 0) {
            throw new IllegalArgumentException("Scale must be greater than zero");
        }
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
        this.colorModel = new IndexColorModel(1, 2, BLACK_WHITE, BLACK_WHITE, BLACK_WHITE);
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
}
