# Repository Guidelines

## Project Structure & Module Organization
- Core source lives in `src/`, with the entry point at `src/main.rs`.
- Add reusable logic in submodules under `src/` (e.g., `src/generator/mod.rs`), keeping files small and purpose-driven.
- Reserve `tests/` for integration tests once scenarios extend beyond unit coverage.
- Place generated artifacts in `target/` only; do not check them into version control.

## Build, Test, and Development Commands
- `cargo build` compiles the application and surfaces type or borrow errors early.
- `cargo run -- <args>` executes the CLI; add sample boards or seed files under `examples/` and reference them here.
- `cargo test` runs unit and integration suites; use it before every push.
- `cargo fmt` enforces formatting; run after edits to ensure consistent diffs.
- `cargo clippy --all-targets` provides lint guidance; fix or annotate warnings with clear justification.

## Coding Style & Naming Conventions
- Follow Rust 2024 edition defaults: 4-space indentation, `snake_case` for functions/modules, `CamelCase` for types, and `SCREAMING_SNAKE_CASE` for constants.
- Group imports by crate and sort alphabetically; prefer explicit `use` paths over glob imports.
- Keep functions under ~40 lines; extract helpers when logic branches multiply.
- Document public items with `///` doc comments when behavior or invariants are non-obvious.

## Testing Guidelines
- Unit tests belong in the same file using `#[cfg(test)]`; integration tests go under `tests/` and exercise executable workflows end-to-end.
- Name tests after the behavior under scrutiny, e.g., `generates_valid_level`.
- Aim to cover happy path, edge conditions (empty board, max grid), and error handling.
- Run `cargo test -- --nocapture` when diagnosing failing scenarios to view detailed output.

## Commit & Pull Request Guidelines
- Write commit subjects in imperative mood (e.g., `Add generator scaffolding`) and keep bodies focused on intent and approach.
- Reference related issues using `Closes #NN` when applicable and group refactors separately from feature work.
- Pull requests should include a short summary, testing evidence (`cargo test` output or equivalent), and screenshots or sample grid files if behavior changes are visual.
- Request review once lint, format, and test checks pass; flag any follow-up tasks in the PR description.
