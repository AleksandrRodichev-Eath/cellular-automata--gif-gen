# Simulation Generator Usage

Run the CLI directly with `cargo run` so changes compile and execute in one step:

```sh
cargo run -- --steps 50 --rule B3/S23 --output target/sample.gif
```

- `--steps` sets the maximum generations to simulate.  
- `--rule` chooses the life-like rule in `B#/S#` format.  
- `--output` points to the desired GIF path; the program appends `_<steps>s` to the filename using the actual number of steps simulated.

Typical workflow:

1. Adjust flags (e.g., `--density 0.02`, `--init-mask 001010111`, `--no-wrap`) to match the scenario you want.
2. Inspect the generated GIF, now named with the step count (e.g., `sample_37s.gif`).
3. Re-run with different settings as needed, or use `cargo test` to validate the suite.
