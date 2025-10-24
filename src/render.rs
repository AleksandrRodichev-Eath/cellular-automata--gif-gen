use std::borrow::Cow;
use std::fs::File;
use std::io::Write;
use std::path::Path;

use anyhow::{Context, Result, ensure};
use gif::{Encoder, Frame, Repeat};

use crate::grid::Grid;

const PALETTE: [u8; 6] = [0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF];

pub struct GifWriter<W: Write> {
    encoder: Encoder<W>,
    width: usize,
    height: usize,
    scale: usize,
    delay: u16,
}

impl GifWriter<File> {
    pub fn create<P: AsRef<Path>>(
        path: P,
        width: usize,
        height: usize,
        scale: usize,
        delay: u16,
    ) -> Result<Self> {
        let path_ref = path.as_ref();
        let file = File::create(path_ref)
            .with_context(|| format!("failed to create output file {}", path_ref.display()))?;
        Self::new(file, width, height, scale, delay)
    }
}

impl<W: Write> GifWriter<W> {
    pub fn new(writer: W, width: usize, height: usize, scale: usize, delay: u16) -> Result<Self> {
        ensure!(scale > 0, "scale must be greater than zero");
        let scaled_width = width.checked_mul(scale).context("scaled width overflow")?;
        let scaled_height = height
            .checked_mul(scale)
            .context("scaled height overflow")?;
        let mut encoder =
            Encoder::new(writer, scaled_width as u16, scaled_height as u16, &PALETTE)?;
        encoder
            .set_repeat(Repeat::Infinite)
            .context("failed to set GIF repeat flag")?;
        Ok(Self {
            encoder,
            width,
            height,
            scale,
            delay,
        })
    }

    pub fn write_frame(&mut self, grid: &Grid) -> Result<()> {
        ensure!(
            grid.width() == self.width && grid.height() == self.height,
            "grid dimensions {}x{} do not match writer dimensions {}x{}",
            grid.width(),
            grid.height(),
            self.width,
            self.height
        );
        let buffer = rasterize(grid, self.scale);
        let mut frame = Frame::default();
        frame.width = (self.width * self.scale) as u16;
        frame.height = (self.height * self.scale) as u16;
        frame.delay = self.delay;
        frame.buffer = Cow::Owned(buffer);
        self.encoder
            .write_frame(&frame)
            .context("failed to write GIF frame")?;
        Ok(())
    }

    pub fn into_inner(self) -> Result<W> {
        self.encoder.into_inner().map_err(Into::into)
    }
}

fn rasterize(grid: &Grid, scale: usize) -> Vec<u8> {
    let scaled_width = grid.width() * scale;
    let scaled_height = grid.height() * scale;
    let mut buffer = vec![0u8; scaled_width * scaled_height];
    for y in 0..grid.height() {
        for x in 0..grid.width() {
            if grid.get(x, y) {
                for sy in 0..scale {
                    let row = (y * scale + sy) * scaled_width;
                    for sx in 0..scale {
                        buffer[row + x * scale + sx] = 1;
                    }
                }
            }
        }
    }
    buffer
}

#[cfg(test)]
mod tests {
    use super::*;
    use gif::DecodeOptions;
    use std::io::Cursor;

    #[test]
    fn gif_header_matches_scaled_dimensions() {
        let grid = Grid::new(2, 2);
        let writer = Cursor::new(Vec::new());
        let mut gif_writer = GifWriter::new(writer, 2, 2, 3, 6).unwrap();
        gif_writer.write_frame(&grid).unwrap();
        let cursor = gif_writer.into_inner().unwrap();
        let bytes = cursor.into_inner();
        assert!(bytes.starts_with(b"GIF89a"));
        assert_eq!(&bytes[6..8], &[6, 0]); // width = 6
        assert_eq!(&bytes[8..10], &[6, 0]); // height = 6
    }

    #[test]
    fn gif_contains_expected_frame_count() {
        let mut grid = Grid::new(2, 2);
        grid.set(0, 0, true);
        let writer = Cursor::new(Vec::new());
        let mut gif_writer = GifWriter::new(writer, 2, 2, 3, 6).unwrap();
        gif_writer.write_frame(&grid).unwrap();
        grid.set(1, 1, true);
        gif_writer.write_frame(&grid).unwrap();
        let cursor = gif_writer.into_inner().unwrap();
        let bytes = cursor.into_inner();

        let mut decoder = DecodeOptions::new();
        decoder.set_color_output(gif::ColorOutput::Indexed);
        let mut reader = decoder.read_info(Cursor::new(bytes)).unwrap();
        let mut frames = 0;
        while let Some(_) = reader.read_next_frame().unwrap() {
            frames += 1;
        }
        assert_eq!(frames, 2);
    }
}
