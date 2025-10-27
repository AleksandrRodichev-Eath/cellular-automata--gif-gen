package com.cellmachine.generator;

import java.util.Arrays;

public final class Grid {

    private final int width;
    private final int height;
    private final boolean[] cells;

    public Grid(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Grid dimensions must be positive");
        }
        this.width = width;
        this.height = height;
        this.cells = new boolean[width * height];
    }

    private Grid(int width, int height, boolean[] cells) {
        this.width = width;
        this.height = height;
        this.cells = cells;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public void set(int x, int y, boolean alive) {
        int idx = index(x, y);
        cells[idx] = alive;
    }

    public boolean get(int x, int y) {
        int idx = index(x, y);
        return cells[idx];
    }

    public int aliveCount() {
        int count = 0;
        for (boolean cell : cells) {
            if (cell) {
                count++;
            }
        }
        return count;
    }

    private int index(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("Coordinates out of range: (" + x + ", " + y + ")");
        }
        return y * width + x;
    }

    public static Grid advance(Grid current, Rule rule, boolean wrap) {
        Grid next = new Grid(current.width, current.height);
        for (int y = 0; y < current.height; y++) {
            for (int x = 0; x < current.width; x++) {
                boolean alive = current.get(x, y);
                int neighbors = neighborCount(current, x, y, wrap);
                boolean nextState = rule.shouldLive(alive, neighbors);
                next.set(x, y, nextState);
            }
        }
        return next;
    }

    static int neighborCount(Grid grid, int x, int y, boolean wrap) {
        int count = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int nx = x + dx;
                int ny = y + dy;
                boolean inBounds = nx >= 0 && nx < grid.width && ny >= 0 && ny < grid.height;
                if (!wrap && !inBounds) {
                    continue;
                }
                int wrappedX;
                int wrappedY;
                if (wrap) {
                    wrappedX = wrapCoordinate(nx, grid.width);
                    wrappedY = wrapCoordinate(ny, grid.height);
                } else if (inBounds) {
                    wrappedX = nx;
                    wrappedY = ny;
                } else {
                    continue;
                }
                if (grid.get(wrappedX, wrappedY)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int wrapCoordinate(int value, int limit) {
        int mod = value % limit;
        if (mod < 0) {
            mod += limit;
        }
        return mod;
    }

    public Grid copy() {
        return new Grid(width, height, Arrays.copyOf(cells, cells.length));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Grid other)) {
            return false;
        }
        return width == other.width && height == other.height && Arrays.equals(cells, other.cells);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(width);
        result = 31 * result + Integer.hashCode(height);
        result = 31 * result + Arrays.hashCode(cells);
        return result;
    }
}
