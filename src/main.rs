mod grid;
mod render;
mod rule;
mod seed;

use std::ffi::OsString;
use std::fs;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use clap::Parser;

use crate::grid::advance;
use crate::render::GifWriter;
use crate::rule::Rule;

const WORLD_WIDTH: usize = 200;
const WORLD_HEIGHT: usize = 200;
const SCALE: usize = 3;
const DEFAULT_STEPS: u32 = 100;
const DEFAULT_DELAY_CS: u16 = 6;

#[derive(Debug, Parser)]
#[command(
    name = "cell-machine-gen-rust",
    about = "Generate animated GIFs of life-like cellular automata",
    version,
    author
)]
struct Cli {
    /// Number of generations to simulate
    #[arg(long, default_value_t = DEFAULT_STEPS)]
    steps: u32,

    /// Life-like rule in B#/S# format (e.g. B3/S23)
    #[arg(long, default_value = "B3/S23")]
    rule: String,

    /// Optional path to a seed file containing coordinates
    #[arg(long)]
    seed: Option<PathBuf>,

    /// Percentage (0.0-1.0) of cells initially alive when no seed file is provided (e.g. 0.01 = 1%)
    #[arg(long)]
    density: Option<f64>,

    /// Output GIF path (defaults to the rule name when omitted)
    #[arg(long)]
    output: Option<PathBuf>,

    /// 3x3 mask (row-major, 0/1) used to seed the grid when no seed file is provided
    #[arg(long)]
    init_mask: Option<String>,

    /// Disable toroidal world wrapping
    #[arg(long)]
    no_wrap: bool,

    /// Frame delay in centiseconds (default 6 = ~10 FPS)
    #[arg(long)]
    delay: Option<u16>,
}

fn main() -> Result<()> {
    let cli = Cli::parse();

    let rule: Rule = cli
        .rule
        .parse()
        .with_context(|| format!("failed to parse rule '{}'", cli.rule))?;

    let init_mask: Option<[bool; 9]> = cli
        .init_mask
        .as_deref()
        .map(seed::parse_init_mask)
        .transpose()?;

    let output_path = cli
        .output
        .clone()
        .unwrap_or_else(|| default_output_path(&cli.rule, init_mask.as_ref(), cli.density));

    let delay = cli.delay.unwrap_or(DEFAULT_DELAY_CS);
    let wrap = !cli.no_wrap;

    let initial_grid = if let Some(seed_path) = cli.seed.as_deref() {
        seed::load_seed_from_file(seed_path, WORLD_WIDTH, WORLD_HEIGHT)?
    } else if let Some(mask) = init_mask.as_ref() {
        if let Some(density) = cli.density {
            seed::random_mask_grid(
                WORLD_WIDTH,
                WORLD_HEIGHT,
                mask,
                density,
                seed::DEFAULT_RANDOM_SEED,
            )?
        } else {
            seed::grid_with_centered_mask(WORLD_WIDTH, WORLD_HEIGHT, mask)?
        }
    } else {
        let density = cli.density.unwrap_or(seed::DEFAULT_RANDOM_DENSITY);
        seed::random_grid(
            WORLD_WIDTH,
            WORLD_HEIGHT,
            density,
            seed::DEFAULT_RANDOM_SEED,
        )?
    };

    let mut writer = GifWriter::create(&output_path, WORLD_WIDTH, WORLD_HEIGHT, SCALE, delay)?;

    let mut current = initial_grid;
    writer.write_frame(&current)?;
    let mut final_alive = current.alive_count();
    let mut steps_taken: u32 = 0;

    for step_index in 0..cli.steps {
        let next = advance(&current, &rule, wrap);
        final_alive = next.alive_count();
        writer.write_frame(&next)?;
        steps_taken = step_index + 1;
        let is_static = next == current;
        current = next;
        if is_static {
            break;
        }
    }

    // Ensure encoder flushes by consuming it, then drop the file handle before renaming.
    let file = writer.into_inner()?;
    drop(file);

    let actual_steps = if steps_taken == 0 {
        cli.steps
    } else {
        steps_taken
    };
    let final_output_path = append_step_suffix(&output_path, actual_steps);
    fs::rename(&output_path, &final_output_path).with_context(|| {
        format!(
            "failed to rename output file to {}",
            final_output_path.display()
        )
    })?;

    println!(
        "Simulated {} generations (requested {}) using rule {}. Final alive cells: {}. Output written to {}.",
        actual_steps,
        cli.steps,
        cli.rule,
        final_alive,
        final_output_path.display()
    );

    Ok(())
}

fn default_output_path(rule: &str, mask: Option<&[bool; 9]>, density: Option<f64>) -> PathBuf {
    let mut components = Vec::new();
    let rule_component = sanitize_rule(rule);
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
    PathBuf::from(format!("{name}.gif"))
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

fn append_step_suffix(path: &Path, steps: u32) -> PathBuf {
    let suffix = format!("_{steps}s");
    let mut new_path = path.to_path_buf();
    let mut file_name = path.file_stem().map(OsString::from).unwrap_or_default();
    file_name.push(&suffix);
    if let Some(ext) = path.extension() {
        file_name.push(".");
        file_name.push(ext);
    }
    new_path.set_file_name(file_name);
    new_path
}
