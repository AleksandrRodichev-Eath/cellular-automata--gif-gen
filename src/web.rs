use std::sync::Arc;

use axum::{
    Json, Router,
    extract::State,
    http::StatusCode,
    response::{IntoResponse, Response},
    routing::post,
};
use serde::{Deserialize, Serialize};
use serde_json::json;

use crate::config::AppConfig;
use crate::generator::{self, SimulationDimensions, SimulationOptions};
use crate::rule::Rule;
use crate::seed;
use crate::telegram::TelegramSender;

#[derive(Clone)]
pub struct AppState {
    pub config: Arc<AppConfig>,
    pub telegram: TelegramSender,
}

pub fn router(state: AppState) -> Router {
    Router::new()
        .route("/generate", post(generate_handler))
        .with_state(state)
}

#[derive(Debug, Deserialize)]
pub struct GenerateRequest {
    #[serde(default)]
    pub steps: Option<u32>,
    #[serde(default)]
    pub rule: Option<String>,
    #[serde(default)]
    pub density: Option<f64>,
    #[serde(default)]
    pub init_mask: Option<String>,
    #[serde(default)]
    pub seed_cells: Vec<SeedCell>,
    #[serde(default)]
    pub wrap: Option<bool>,
    #[serde(default)]
    pub delay: Option<u16>,
    #[serde(default)]
    pub width: Option<usize>,
    #[serde(default)]
    pub height: Option<usize>,
    #[serde(default)]
    pub scale: Option<usize>,
    #[serde(default)]
    pub caption: Option<String>,
    #[serde(default)]
    pub random_seed: Option<u64>,
}

#[derive(Debug, Deserialize)]
pub struct SeedCell {
    pub x: usize,
    pub y: usize,
}

#[derive(Debug, Serialize)]
pub struct GenerateResponse {
    pub file_name: String,
    pub steps_requested: u32,
    pub steps_simulated: u32,
    pub final_alive: usize,
    pub rule: String,
    pub grid_width: usize,
    pub grid_height: usize,
    pub scale: usize,
    pub wrap: bool,
    pub delay_cs: u16,
    pub requested_density: Option<f64>,
    pub effective_density: Option<f64>,
    pub init_mask: Option<String>,
    pub seed_cell_count: Option<usize>,
    pub random_seed: u64,
    pub summary: String,
    pub message: String,
}

#[derive(Debug)]
struct ApiError {
    status: StatusCode,
    message: String,
}

impl ApiError {
    fn new(status: StatusCode, message: impl Into<String>) -> Self {
        Self {
            status,
            message: message.into(),
        }
    }

    fn bad_request(message: impl Into<String>) -> Self {
        Self::new(StatusCode::BAD_REQUEST, message)
    }

    fn internal(message: impl Into<String>) -> Self {
        Self::new(StatusCode::INTERNAL_SERVER_ERROR, message)
    }
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let payload = json!({ "error": self.message });
        (self.status, Json(payload)).into_response()
    }
}

async fn generate_handler(
    State(state): State<AppState>,
    Json(request): Json<GenerateRequest>,
) -> Result<Json<GenerateResponse>, ApiError> {
    let rule_label = request.rule.clone().unwrap_or_else(|| "B3/S23".to_owned());
    let rule = rule_label
        .parse::<Rule>()
        .map_err(|err| ApiError::bad_request(format!("invalid rule: {err}")))?;

    let init_mask = request
        .init_mask
        .as_deref()
        .map(seed::parse_init_mask)
        .transpose()
        .map_err(|err| ApiError::bad_request(err.to_string()))?;

    let seed_cells: Option<Vec<(usize, usize)>> = if request.seed_cells.is_empty() {
        None
    } else {
        Some(
            request
                .seed_cells
                .iter()
                .map(|cell| (cell.x, cell.y))
                .collect(),
        )
    };

    let dimensions = SimulationDimensions {
        width: request.width.unwrap_or(generator::DEFAULT_WORLD_WIDTH),
        height: request.height.unwrap_or(generator::DEFAULT_WORLD_HEIGHT),
        scale: request.scale.unwrap_or(generator::DEFAULT_SCALE),
    };

    let options = SimulationOptions {
        steps: request.steps.unwrap_or(generator::DEFAULT_STEPS),
        rule,
        rule_label: rule_label.clone(),
        density: request.density,
        init_mask,
        seed_cells,
        wrap: request.wrap.unwrap_or(true),
        delay: request.delay.unwrap_or(generator::DEFAULT_DELAY_CS),
        dimensions,
        random_seed: request.random_seed.unwrap_or(seed::DEFAULT_RANDOM_SEED),
    };

    let result = generator::run_simulation(options)
        .map_err(|err| ApiError::bad_request(format!("simulation failed: {err}")))?;

    let mut message = result.summary.clone();
    if let Some(extra) = request
        .caption
        .as_deref()
        .map(str::trim)
        .filter(|text| !text.is_empty())
    {
        message.push_str("\n\n");
        message.push_str(extra);
    }

    state
        .telegram
        .send_document(&result.file_name, &result.gif_bytes, &message)
        .await
        .map_err(|err| ApiError::internal(format!("failed to send GIF: {err}")))?;

    let response = GenerateResponse {
        file_name: result.file_name,
        steps_requested: result.steps_requested,
        steps_simulated: result.steps_simulated,
        final_alive: result.final_alive,
        rule: rule_label,
        grid_width: result.dimensions.width,
        grid_height: result.dimensions.height,
        scale: result.dimensions.scale,
        wrap: result.wrap,
        delay_cs: result.delay_cs,
        requested_density: result.requested_density,
        effective_density: result.effective_density,
        init_mask: result.init_mask_label,
        seed_cell_count: result.seed_cell_count,
        random_seed: result.random_seed,
        summary: result.summary,
        message,
    };
    Ok(Json(response))
}
