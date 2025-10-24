use assert_cmd::Command;
use predicates::prelude::*;
use std::fs;
use tempfile::tempdir;

#[test]
fn generates_gif_with_expected_dimensions() {
    let tmp_dir = tempdir().unwrap();
    let requested_output_path = tmp_dir.path().join("automation.gif");

    Command::cargo_bin("cell-machine-gen-rust")
        .unwrap()
        .args([
            "--steps",
            "3",
            "--rule",
            "B3/S23",
            "--output",
            requested_output_path.to_str().unwrap(),
            "--delay",
            "5",
        ])
        .assert()
        .success()
        .stdout(predicate::str::contains("Simulated"));

    let mut gif_paths: Vec<_> = fs::read_dir(tmp_dir.path())
        .unwrap()
        .filter_map(|entry| {
            entry.ok().map(|e| e.path()).filter(|path| {
                path.extension()
                    .and_then(|ext| ext.to_str())
                    .map(|ext| ext.eq_ignore_ascii_case("gif"))
                    .unwrap_or(false)
            })
        })
        .collect();
    assert_eq!(gif_paths.len(), 1, "expected a single GIF output");
    let output_path = gif_paths.pop().unwrap();
    let stem = output_path.file_stem().unwrap().to_string_lossy();
    assert!(
        stem.contains('_') && stem.ends_with('s'),
        "expected filename to include step suffix, got {}",
        output_path.display()
    );

    let bytes = fs::read(&output_path).unwrap();
    assert!(bytes.starts_with(b"GIF89a"));
    // Logical screen width/height stored little-endian at bytes 6..10
    assert_eq!(&bytes[6..8], &[0x58, 0x02]); // 600 width
    assert_eq!(&bytes[8..10], &[0x58, 0x02]); // 600 height
}
