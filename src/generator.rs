use std::fs;
use std::io::Cursor;
use std::path::PathBuf;

use anyhow::{Context, Result, ensure};

use crate::grid::{self, Grid};
use crate::render::GifWriter;
use crate::rule::Rule;
use crate::seed;

pub const DEFAULT_WORLD_WIDTH: usize = 200;
pub const DEFAULT_WORLD_HEIGHT: usize = 200;
pub const DEFAULT_SCALE: usize = 6;
pub const DEFAULT_STEPS: u32 = 100;
pub const DEFAULT_DELAY_CS: u16 = 6;

#[derive(Clone, Copy, Debug, Default)]
pub struct SimulationDimensions {
    pub width: usize,
    pub height: usize,
    pub scale: usize,
}

impl SimulationDimensions {
    pub fn with_defaults() -> Self {
        Self {
            width: DEFAULT_WORLD_WIDTH,
            height: DEFAULT_WORLD_HEIGHT,
            scale: DEFAULT_SCALE,
        }
    }
}

#[derive(Clone, Debug)]
pub struct SimulationOptions {
    pub steps: u32,
    pub rule: Rule,
    pub rule_label: String,
    pub density: Option<f64>,
    pub init_mask: Option<[bool; 9]>,
    pub seed_cells: Option<Vec<(usize, usize)>>,
    pub wrap: bool,
    pub delay: u16,
    pub dimensions: SimulationDimensions,
    pub random_seed: u64,
}

impl Default for SimulationOptions {
    fn default() -> Self {
        Self {
            steps: DEFAULT_STEPS,
            rule: Rule::default(),
            rule_label: "B3/S23".to_owned(),
            density: None,
            init_mask: None,
            seed_cells: None,
            wrap: true,
            delay: DEFAULT_DELAY_CS,
            dimensions: SimulationDimensions::with_defaults(),
            random_seed: seed::DEFAULT_RANDOM_SEED,
        }
    }
}

#[derive(Debug)]
pub struct SimulationResult {
    pub gif_bytes: Vec<u8>,
    pub file_name: String,
    pub steps_requested: u32,
    pub steps_simulated: u32,
    pub final_alive: usize,
    pub rule_label: String,
    pub dimensions: SimulationDimensions,
    pub delay_cs: u16,
    pub wrap: bool,
    pub requested_density: Option<f64>,
    pub effective_density: Option<f64>,
    pub init_mask_label: Option<String>,
    pub seed_cell_count: Option<usize>,
    pub random_seed: u64,
    pub summary: String,
}

pub fn run_simulation(opts: SimulationOptions) -> Result<SimulationResult> {
    let SimulationOptions {
        steps,
        rule,
        rule_label,
        density,
        init_mask,
        seed_cells,
        wrap,
        delay,
        dimensions,
        random_seed,
    } = opts;

    ensure!(dimensions.width > 0, "width must be positive");
    ensure!(dimensions.height > 0, "height must be positive");
    ensure!(dimensions.scale > 0, "scale must be positive");

    let SimulationDimensions {
        width,
        height,
        scale,
    } = dimensions;

    let init_mask_label = init_mask.as_ref().map(|mask| seed::mask_to_label(mask));
    let seed_cell_count = seed_cells.as_ref().map(|cells| cells.len());

    let initial_grid = build_initial_grid(
        width,
        height,
        init_mask.as_ref(),
        density,
        seed_cells.as_ref(),
        random_seed,
    )?;

    let mut writer = GifWriter::new(Cursor::new(Vec::new()), width, height, scale, delay)?;
    writer
        .write_frame(&initial_grid)
        .context("failed to write initial frame")?;

    let mut current = initial_grid;
    let mut steps_taken = 0;

    for step_index in 0..steps {
        let next = grid::advance(&current, &rule, wrap);
        writer
            .write_frame(&next)
            .with_context(|| format!("failed to write frame {}", step_index + 1))?;
        steps_taken = step_index + 1;
        if next == current {
            current = next;
            break;
        }
        current = next;
    }

    let cursor = writer.into_inner()?;
    let bytes = cursor.into_inner();

    let final_alive = current.alive_count();
    let steps_simulated = if steps_taken == 0 { steps } else { steps_taken };

    let base_name = default_output_name(&rule_label, init_mask.as_ref(), density);
    let file_name = append_step_suffix(&base_name, steps_simulated);

    let effective_density = if seed_cells.is_some() {
        None
    } else if init_mask.is_some() {
        density
    } else {
        Some(density.unwrap_or(seed::DEFAULT_RANDOM_DENSITY))
    };

    let seed_description = describe_seed(&init_mask_label, density, seed_cell_count);
    let used_randomness = seed_cells.is_none() && (init_mask.is_none() || density.is_some());
    let summary = build_summary(
        steps,
        steps_simulated,
        &rule_label,
        final_alive,
        &file_name,
        dimensions,
        wrap,
        delay,
        &seed_description,
        used_randomness,
        random_seed,
    );

    Ok(SimulationResult {
        gif_bytes: bytes,
        file_name,
        steps_requested: steps,
        steps_simulated,
        final_alive,
        rule_label,
        dimensions,
        delay_cs: delay,
        wrap,
        requested_density: density,
        effective_density,
        init_mask_label,
        seed_cell_count,
        random_seed,
        summary,
    })
}

fn build_initial_grid(
    width: usize,
    height: usize,
    init_mask: Option<&[bool; 9]>,
    density: Option<f64>,
    seed_cells: Option<&Vec<(usize, usize)>>,
    random_seed: u64,
) -> Result<Grid> {
    if let Some(cells) = seed_cells {
        let mut grid = Grid::new(width, height);
        for &(x, y) in cells {
            ensure!(x < width, "seed cell x={} exceeds width {}", x, width);
            ensure!(y < height, "seed cell y={} exceeds height {}", y, height);
            grid.set(x, y, true);
        }
        return Ok(grid);
    }

    if let Some(mask) = init_mask {
        if let Some(density) = density {
            return seed::random_mask_grid(width, height, mask, density, random_seed);
        }
        return seed::grid_with_centered_mask(width, height, mask);
    }

    let density = density.unwrap_or(seed::DEFAULT_RANDOM_DENSITY);
    seed::random_grid(width, height, density, random_seed)
}

fn default_output_name(rule: &str, mask: Option<&[bool; 9]>, density: Option<f64>) -> String {
    let rule_component = sanitize_rule(rule);
    let mut components = Vec::new();
    if rule_component.is_empty() {
        components.push("life".to_owned());
    } else {
        components.push(rule_component);
    }
    if let Some(mask) = mask {
        components.push(seed::mask_to_label(mask));
    }
    if let Some(density) = density {
        components.push(format_density(density));
    }
    let name = components.join("_");
    format!("{name}.gif")
}

fn describe_seed(
    init_mask_label: &Option<String>,
    density: Option<f64>,
    seed_cell_count: Option<usize>,
) -> String {
    if let Some(count) = seed_cell_count {
        return format!("manual coordinates ({count} points)");
    }

    if let Some(mask_label) = init_mask_label {
        if let Some(density) = density {
            return format!("mask {} randomized at {:.1}%", mask_label, density * 100.0);
        }
        return format!("mask {} centered", mask_label);
    }

    let effective_density = density.unwrap_or(seed::DEFAULT_RANDOM_DENSITY);
    format!("random {:.1}% density", effective_density * 100.0)
}

fn build_summary(
    steps_requested: u32,
    steps_simulated: u32,
    rule_label: &str,
    final_alive: usize,
    file_name: &str,
    dimensions: SimulationDimensions,
    wrap: bool,
    delay_cs: u16,
    seed_description: &str,
    used_randomness: bool,
    random_seed: u64,
) -> String {
    let wrap_label = if wrap {
        "toroidal wrap"
    } else {
        "bounded edges"
    };
    let mut parts = vec![
        format!(
            "Simulated {} generations (requested {}) using rule {}.",
            steps_simulated, steps_requested, rule_label
        ),
        format!("Final alive cells: {}.", final_alive),
        format!(
            "Grid {}x{} (scale {}, {}).",
            dimensions.width, dimensions.height, dimensions.scale, wrap_label
        ),
        format!("Frame delay: {}cs.", delay_cs),
        format!("Seed: {}.", seed_description),
    ];
    if used_randomness {
        parts.push(format!("RNG seed: {}.", random_seed));
    }
    parts.push(format!("File tag: {}.", file_name));
    parts.join(" ")
}

fn sanitize_rule(rule: &str) -> String {
    let sanitized: String = rule
        .trim()
        .chars()
        .map(|c| match c {
            'A'..='Z' | 'a'..='z' | '0'..='9' => c,
            _ => '_',
        })
        .collect();
    sanitized.trim_matches('_').to_owned()
}

fn format_density(density: f64) -> String {
    let percent = (density * 100.0).round();
    let percent = percent.max(0.0).min(100.0);
    let percent_int = percent as i64;
    percent_int.to_string()
}

fn append_step_suffix(name: &str, steps: u32) -> String {
    let suffix = format!("_{steps}s");
    match name.rsplit_once('.') {
        Some((base, ext)) if !ext.is_empty() => format!("{base}{suffix}.{ext}"),
        _ => format!("{name}{suffix}"),
    }
}

pub fn persist_last_gif(bytes: &[u8]) -> Result<PathBuf> {
    let dir = PathBuf::from("gif");
    fs::create_dir_all(&dir).context("failed to create gif directory")?;
    let path = dir.join("last.gif");
    fs::write(&path, bytes).context("failed to write last GIF")?;
    Ok(path)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn append_step_suffix_inserts_before_extension() {
        let result = append_step_suffix("sample.gif", 42);
        assert_eq!(result, "sample_42s.gif");
    }

    #[test]
    fn build_initial_grid_from_seed_cells() {
        let grid = build_initial_grid(
            5,
            5,
            None,
            None,
            Some(&vec![(1, 1), (2, 2)]),
            seed::DEFAULT_RANDOM_SEED,
        )
        .unwrap();
        assert!(grid.get(1, 1));
        assert!(grid.get(2, 2));
        assert_eq!(grid.alive_count(), 2);
    }
}
