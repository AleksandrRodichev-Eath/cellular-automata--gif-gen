//! Application configuration types.

use std::env;
use std::net::SocketAddr;

use anyhow::{Context, Result};

#[derive(Clone, Debug)]
pub struct AppConfig {
    pub telegram_bot_token: String,
    pub telegram_chat_id: String,
    pub bind_address: SocketAddr,
}

impl AppConfig {
    pub fn from_env() -> Result<Self> {
        let telegram_bot_token = env::var("TELEGRAM_BOT_TOKEN")
            .context("TELEGRAM_BOT_TOKEN environment variable is required")?;
        let telegram_chat_id = env::var("TELEGRAM_CHAT_ID")
            .context("TELEGRAM_CHAT_ID environment variable is required")?;

        // let telegram_bot_token = "8132408470:AAFS0HljpiLHPf6NGzmXIHP_JToclcWW7zQ";
        // let telegram_chat_id = "B3S23";
        let bind_address = resolve_bind_address()?;

        Ok(Self {
            telegram_bot_token,
            telegram_chat_id,
            bind_address,
        })
    }
}

fn resolve_bind_address() -> Result<SocketAddr> {
    if let Ok(addr) = env::var("APP_BIND_ADDR") {
        return addr
            .parse::<SocketAddr>()
            .context("failed to parse APP_BIND_ADDR");
    }
    let port: u16 = env::var("PORT")
        .ok()
        .and_then(|value| value.parse().ok())
        .unwrap_or(3000);
    let addr = format!("0.0.0.0:{port}");
    addr.parse::<SocketAddr>()
        .context("failed to parse computed bind address")
}
