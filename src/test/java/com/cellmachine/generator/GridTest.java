package com.cellmachine.generator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GridTest {

    private Grid blinkerInitial() {
        Grid grid = new Grid(5, 5);
        grid.set(2, 1, true);
        grid.set(2, 2, true);
        grid.set(2, 3, true);
        return grid;
    }

    @Test
    void blinkerOscillatesWithoutWrap() {
        Rule rule = Rule.defaultLife();
        Grid initial = blinkerInitial();
        Grid first = Grid.advance(initial, rule, false);
        assertTrue(first.get(1, 2));
        assertTrue(first.get(2, 2));
        assertTrue(first.get(3, 2));
        assertEquals(3, first.aliveCount());

        Grid second = Grid.advance(first, rule, false);
        assertEquals(3, second.aliveCount());
        assertTrue(second.get(2, 1));
        assertTrue(second.get(2, 2));
        assertTrue(second.get(2, 3));
    }

    @Test
    void wraparoundCountsNeighbors() {
        Grid grid = new Grid(3, 3);
        grid.set(2, 2, true);
        assertEquals(0, Grid.neighborCount(grid, 0, 0, false));
        assertEquals(1, Grid.neighborCount(grid, 0, 0, true));
    }
}
