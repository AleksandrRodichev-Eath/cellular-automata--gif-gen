use std::fs::File;
use std::io::{BufRead, BufReader};
use std::path::Path;

use anyhow::{Context, Result, anyhow, bail, ensure};
use rand::rngs::StdRng;
use rand::{Rng, SeedableRng};

use crate::grid::Grid;

pub const DEFAULT_RANDOM_DENSITY: f64 = 0.15;
pub const DEFAULT_RANDOM_SEED: u64 = 0x5EED5EED;

pub fn load_seed_from_file(path: &Path, width: usize, height: usize) -> Result<Grid> {
    let file =
        File::open(path).with_context(|| format!("failed to open seed file {}", path.display()))?;
    let reader = BufReader::new(file);
    let mut grid = Grid::new(width, height);

    for (line_no, line) in reader.lines().enumerate() {
        let line =
            line.with_context(|| format!("failed reading line {} in seed file", line_no + 1))?;
        let trimmed = line.trim();
        if trimmed.is_empty() || trimmed.starts_with('#') {
            continue;
        }
        let mut parts = trimmed.split_whitespace();
        let x_str = parts
            .next()
            .ok_or_else(|| invalid_line(line_no + 1, trimmed))?;
        let y_str = parts
            .next()
            .ok_or_else(|| invalid_line(line_no + 1, trimmed))?;
        if parts.next().is_some() {
            bail!("line {} has extra data: '{trimmed}'", line_no + 1);
        }
        let x = x_str
            .parse::<usize>()
            .with_context(|| format!("invalid x coordinate '{}' on line {}", x_str, line_no + 1))?;
        let y = y_str
            .parse::<usize>()
            .with_context(|| format!("invalid y coordinate '{}' on line {}", y_str, line_no + 1))?;
        ensure!(
            x < width && y < height,
            "coordinate ({x}, {y}) on line {} is outside the {width}x{height} grid",
            line_no + 1
        );
        grid.set(x, y, true);
    }

    Ok(grid)
}

pub fn random_grid(width: usize, height: usize, density: f64, seed: u64) -> Result<Grid> {
    ensure!(
        (0.0..=1.0).contains(&density),
        "density must be between 0.0 and 1.0 inclusive"
    );
    ensure!(width > 0 && height > 0, "grid dimensions must be positive");
    let mut rng = StdRng::seed_from_u64(seed);
    let mut grid = Grid::new(width, height);
    for y in 0..height {
        for x in 0..width {
            if rng.r#gen::<f64>() < density {
                grid.set(x, y, true);
            }
        }
    }
    Ok(grid)
}

pub fn parse_init_mask(raw: &str) -> Result<[bool; 9]> {
    let mut entries = Vec::with_capacity(9);
    for ch in raw.chars() {
        if ch.is_ascii_whitespace() {
            continue;
        }
        match ch {
            '0' => entries.push(false),
            '1' => entries.push(true),
            _ => bail!("init mask must contain only '0' or '1' characters"),
        };
    }
    ensure!(
        entries.len() == 9,
        "init mask must contain exactly 9 entries (3x3 matrix)"
    );
    let mut mask = [false; 9];
    for (idx, value) in entries.into_iter().enumerate() {
        mask[idx] = value;
    }
    Ok(mask)
}

pub fn grid_with_centered_mask(width: usize, height: usize, mask: &[bool; 9]) -> Result<Grid> {
    ensure!(
        width >= 3 && height >= 3,
        "grid must be at least 3x3 for init mask"
    );
    let mut grid = Grid::new(width, height);
    let base_x = (width - 3) / 2;
    let base_y = (height - 3) / 2;
    apply_mask(&mut grid, base_x, base_y, mask);
    Ok(grid)
}

pub fn random_mask_grid(
    width: usize,
    height: usize,
    mask: &[bool; 9],
    density: f64,
    seed: u64,
) -> Result<Grid> {
    ensure!(
        width >= 3 && height >= 3,
        "grid must be at least 3x3 for init mask"
    );
    ensure!(
        (0.0..=1.0).contains(&density),
        "density must be between 0.0 and 1.0 inclusive"
    );
    let active_cells = mask.iter().filter(|&&b| b).count();
    ensure!(
        active_cells > 0 || density == 0.0,
        "init mask must contain at least one active cell when density is greater than zero"
    );

    let mut grid = Grid::new(width, height);
    if density == 0.0 || active_cells == 0 {
        return Ok(grid);
    }

    let target_alive = ((width * height) as f64 * density).round() as usize;
    if target_alive == 0 {
        return Ok(grid);
    }

    let shapes_needed = (target_alive + active_cells - 1) / active_cells;
    let max_attempts = shapes_needed * 10 + 100;
    let mut rng = StdRng::seed_from_u64(seed);
    let max_x = width - 3;
    let max_y = height - 3;
    let mask_offsets: Vec<(usize, usize)> = mask
        .iter()
        .enumerate()
        .filter_map(|(idx, &active)| {
            if active {
                Some((idx % 3, idx / 3))
            } else {
                None
            }
        })
        .collect();

    let mut attempts = 0;
    while grid.alive_count() < target_alive && attempts < max_attempts {
        let x0 = if max_x == 0 {
            0
        } else {
            rng.gen_range(0..=max_x)
        };
        let y0 = if max_y == 0 {
            0
        } else {
            rng.gen_range(0..=max_y)
        };
        for (dx, dy) in &mask_offsets {
            grid.set(x0 + dx, y0 + dy, true);
        }
        attempts += 1;
    }

    Ok(grid)
}

fn apply_mask(grid: &mut Grid, base_x: usize, base_y: usize, mask: &[bool; 9]) {
    for (idx, &active) in mask.iter().enumerate() {
        if !active {
            continue;
        }
        let dx = idx % 3;
        let dy = idx / 3;
        grid.set(base_x + dx, base_y + dy, true);
    }
}

pub fn mask_to_label(mask: &[bool; 9]) -> String {
    mask.iter()
        .map(|&active| if active { '1' } else { '0' })
        .collect()
}

fn invalid_line(line_no: usize, line: &str) -> anyhow::Error {
    anyhow!("line {} must contain two integers: '{line}'", line_no)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    use tempfile::NamedTempFile;

    #[test]
    fn loads_coordinates_from_file() {
        let mut file = NamedTempFile::new().unwrap();
        writeln!(file, "1 2\n# comment\n3 4").unwrap();
        let grid = load_seed_from_file(file.path(), 5, 5).unwrap();
        assert!(grid.get(1, 2));
        assert!(grid.get(3, 4));
        assert_eq!(grid.alive_count(), 2);
    }

    #[test]
    fn rejects_out_of_bounds_coordinates() {
        let mut file = NamedTempFile::new().unwrap();
        writeln!(file, "5 0").unwrap();
        let err = load_seed_from_file(file.path(), 5, 5).unwrap_err();
        assert!(err.to_string().contains("outside"));
    }

    #[test]
    fn random_grid_respects_density() {
        let grid = random_grid(10, 10, 0.5, 42).unwrap();
        let alive = grid.alive_count();
        assert!(alive > 0);
        assert!(alive < 100);
    }

    #[test]
    fn parse_init_mask_accepts_binary_string() {
        let mask = parse_init_mask("111101111").unwrap();
        assert_eq!(mask.iter().filter(|&&b| b).count(), 8);
    }

    #[test]
    fn parse_init_mask_rejects_invalid_length() {
        let err = parse_init_mask("1010").unwrap_err();
        assert!(err.to_string().contains("exactly 9"));
    }

    #[test]
    fn grid_with_centered_mask_places_cells() {
        let mask = parse_init_mask("000111000").unwrap();
        let grid = grid_with_centered_mask(5, 5, &mask).unwrap();
        assert_eq!(grid.alive_count(), 3);
        assert!(grid.get(2, 2));
    }

    #[test]
    fn random_mask_grid_spawns_shapes() {
        let mask = parse_init_mask("000111000").unwrap();
        let grid = random_mask_grid(10, 10, &mask, 0.1, 1234).unwrap();
        assert!(grid.alive_count() >= 3);
    }

    #[test]
    fn mask_to_label_returns_binary_string() {
        let mask = parse_init_mask("101010101").unwrap();
        assert_eq!(mask_to_label(&mask), "101010101");
    }
}
