use std::sync::Arc;

use anyhow::Result;
use tokio::net::TcpListener;
use tracing_subscriber::EnvFilter;

use cell_machine_gen_rust::config::AppConfig;
use cell_machine_gen_rust::scheduler;
use cell_machine_gen_rust::telegram::TelegramSender;
use cell_machine_gen_rust::web::{self, AppState};

#[tokio::main]
async fn main() -> Result<()> {
    init_tracing();

    let config = Arc::new(AppConfig::from_env()?);
    let bind_address = config.bind_address;
    tracing::info!("Starting server on {}", bind_address);

    let telegram = TelegramSender::new(
        config.telegram_bot_token.clone(),
        config.telegram_chat_id.clone(),
    )
    .await?;

    let state = AppState {
        config: Arc::clone(&config),
        telegram,
    };

    scheduler::send_startup_snapshot(&state).await?;
    scheduler::spawn_daily_tasks(state.clone());

    let app = web::router(state);
    let listener = TcpListener::bind(bind_address).await?;
    axum::serve(listener, app).await?;

    Ok(())
}

fn init_tracing() {
    let env_filter = EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info"));
    let _ = tracing_subscriber::fmt()
        .with_env_filter(env_filter)
        .try_init();
}
