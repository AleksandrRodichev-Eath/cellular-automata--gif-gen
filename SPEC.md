# Cellular Automaton GIF Specification

## Goal
Produce a Rust CLI that simulates a Cellular Automaton on a 200x200 logical grid and exports the evolution as a 600x600 pixel animated GIF. Each logical cell occupies a 3x3 pixel block in the rasterized frames.

## Functional Requirements
- **CLI parameters**
  - `--steps <u32>`: positive number of generations to simulate (default: 100).
  - `--rule <string>`: life-like rule string in `B{digits}/S{digits}` format (e.g., `B3/S23`).
  - `--seed <path>` (optional): newline-delimited coordinates `x y` (0-based) marking initially alive cells. Absent => deterministic pseudo-random seed using a fixed `StdRng` seed.
  - `--output <path>` (optional): GIF filename (default: `life.gif` in CWD).
- **Rule parsing**: validate format, reject duplicates, and map to born/survive lookup tables (0–8 neighbors).
- **Simulation loop**
  - Represent grid as `Vec<bool>` or bitset; update concurrently using double-buffering.
  - Neighbor lookup wraps around (toroidal world) unless `--no-wrap` flag is provided.
- **Rendering**
  - Each frame draws alive cells as white (`#FFFFFF`), dead as black (`#000000`).
  - Frame delay defaults to 6 centiseconds (~10 FPS); configurable via `--delay <u16>`.
  - Generate `steps + 1` frames (include initial state).
- **Output**
  - Write animated GIF to disk; ensure file closes cleanly.
  - Print summary: steps simulated, alive count final step, output path.

## Architectural Outline
- `main.rs`: CLI parsing via `clap`.
- `sim` module: rule parsing, grid types, neighbor iteration, step advancement.
- `render` module: frame rasterization and GIF encoding.
- `seed` module: load from file or generate deterministic random population density (default 15% alive cells).

## Dependencies
- `clap` (`derive` feature) for argument parsing.
- `gif` crate for GIF encoding (writer with palette support).
- `rand` with `StdRng` for deterministic seeding.
- `colorous` (optional) if colored themes required later; start monochrome.

## Testing Strategy
- Unit tests for rule parsing (valid/invalid, deduplicated digits).
- Property-style tests ensuring evolution obeys rules on random boards (use small 5x5 grids).
- Golden file comparison for GIF metadata: verify frame count, logical screen size, delay.
- CLI smoke tests using `assert_cmd` and `predicates` to validate exit codes and outputs.

## Build & Tooling
- Use `cargo fmt`, `cargo clippy --all-targets`, and `cargo test` as pre-commit checks.
- Provide `Makefile` aliases (`make build`, `make gif`) if workflow expands.

## Open Questions
- Should seeding support bitmap imports (`.rle`, `.cells`)? Default to coordinate list until requested.
- Any need for color themes or trails? Current scope keeps binary palette.
