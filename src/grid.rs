use crate::rule::Rule;

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Grid {
    width: usize,
    height: usize,
    cells: Vec<bool>,
}

impl Grid {
    pub fn new(width: usize, height: usize) -> Self {
        Self {
            width,
            height,
            cells: vec![false; width * height],
        }
    }

    pub fn width(&self) -> usize {
        self.width
    }

    pub fn height(&self) -> usize {
        self.height
    }

    pub fn index(&self, x: usize, y: usize) -> usize {
        y * self.width + x
    }

    pub fn set(&mut self, x: usize, y: usize, alive: bool) {
        let idx = self.index(x, y);
        self.cells[idx] = alive;
    }

    pub fn get(&self, x: usize, y: usize) -> bool {
        let idx = self.index(x, y);
        self.cells[idx]
    }

    pub fn alive_count(&self) -> usize {
        self.cells.iter().filter(|&&alive| alive).count()
    }
}

pub fn advance(grid: &Grid, rule: &Rule, wrap: bool) -> Grid {
    let mut next = Grid::new(grid.width, grid.height);
    for y in 0..grid.height {
        for x in 0..grid.width {
            let alive = grid.get(x, y);
            let neighbors = neighbor_count(grid, x, y, wrap);
            let state = rule.should_live(alive, neighbors);
            next.set(x, y, state);
        }
    }
    next
}

fn neighbor_count(grid: &Grid, x: usize, y: usize, wrap: bool) -> u8 {
    let mut count = 0;
    for dy in [-1, 0, 1] {
        for dx in [-1, 0, 1] {
            if dx == 0 && dy == 0 {
                continue;
            }
            let nx = x as isize + dx;
            let ny = y as isize + dy;
            let in_bounds =
                nx >= 0 && nx < grid.width as isize && ny >= 0 && ny < grid.height as isize;
            if !wrap && !in_bounds {
                continue;
            }
            let wrapped_x = if wrap {
                ((nx % grid.width as isize) + grid.width as isize) as usize % grid.width
            } else if in_bounds {
                nx as usize
            } else {
                continue;
            };
            let wrapped_y = if wrap {
                ((ny % grid.height as isize) + grid.height as isize) as usize % grid.height
            } else if in_bounds {
                ny as usize
            } else {
                continue;
            };
            if grid.get(wrapped_x, wrapped_y) {
                count += 1;
            }
        }
    }
    count
}

#[cfg(test)]
mod tests {
    use super::*;

    fn blinker_initial() -> Grid {
        let mut grid = Grid::new(5, 5);
        grid.set(2, 1, true);
        grid.set(2, 2, true);
        grid.set(2, 3, true);
        grid
    }

    #[test]
    fn blinker_oscillates_without_wrap() {
        let rule = Rule::default();
        let initial = blinker_initial();
        let first = advance(&initial, &rule, false);
        assert!(first.get(1, 2));
        assert!(first.get(2, 2));
        assert!(first.get(3, 2));
        assert_eq!(first.alive_count(), 3);
        let second = advance(&first, &rule, false);
        assert_eq!(second.alive_count(), 3);
        assert!(second.get(2, 1));
        assert!(second.get(2, 2));
        assert!(second.get(2, 3));
    }

    #[test]
    fn wraparound_neighbors_count() {
        let mut grid = Grid::new(3, 3);
        grid.set(2, 2, true);
        assert_eq!(neighbor_count(&grid, 0, 0, false), 0);
        assert_eq!(neighbor_count(&grid, 0, 0, true), 1);
    }
}
