use std::fs;
use std::path::PathBuf;
use std::sync::Arc;

use axum::body::{self, Body};
use axum::http::{Request, StatusCode};
use axum::response::IntoResponse;
use axum::routing::post;
use axum::{Json, Router};
use cell_machine_gen_rust::config::AppConfig;
use cell_machine_gen_rust::telegram::TelegramSender;
use cell_machine_gen_rust::web::{self, AppState};
use serde_json::{Value, json};
use tokio::net::TcpListener;
use tower::ServiceExt;

#[tokio::test]
async fn generate_endpoint_succeeds_with_stubbed_telegram() {
    let telegram_listener = TcpListener::bind("127.0.0.1:0")
        .await
        .expect("stub telegram bind");
    let telegram_addr = telegram_listener.local_addr().unwrap();
    tokio::spawn(async move {
        let app = Router::new()
            .route(
                "/botTESTTOKEN/sendAnimation",
                post(telegram_send_animation_stub),
            )
            .route("/botTESTTOKEN/getMe", post(telegram_get_me_stub));
        axum::serve(telegram_listener, app)
            .await
            .expect("serve telegram stub");
    });

    let base_url = format!("http://{}", telegram_addr);
    let telegram_sender =
        TelegramSender::with_base_url(base_url, "TESTTOKEN".to_owned(), "@channel".to_owned())
            .await
            .expect("telegram sender");

    let config = Arc::new(AppConfig {
        telegram_bot_token: "TESTTOKEN".to_owned(),
        telegram_chat_id: "@channel".to_owned(),
        bind_address: "127.0.0.1:0".parse().unwrap(),
    });

    let state = AppState {
        config,
        telegram: telegram_sender,
    };

    let app = web::router(state);
    let last_path = PathBuf::from("gif/last.gif");
    let previous = fs::read(&last_path).ok();

    let request_body = serde_json::json!({
        "steps": 2,
        "rule": "B3/S23",
        "caption": "stubbed",
        "random_seed": 1234,
        "width": 10,
        "height": 10,
        "scale": 1
    });
    let request = Request::builder()
        .method("POST")
        .uri("/generate")
        .header("content-type", "application/json")
        .body(Body::from(request_body.to_string()))
        .unwrap();

    let response = app.clone().oneshot(request).await.unwrap();
    assert!(response.status().is_success());
    let bytes = body::to_bytes(response.into_body(), 1_000_000)
        .await
        .unwrap();
    let payload: Value = serde_json::from_slice(&bytes).unwrap();
    assert_eq!(payload["rule"], "B3/S23");
    assert_eq!(payload["steps_simulated"], 2);
    let summary = payload["summary"].as_str().unwrap();
    assert!(summary.contains("Simulated 2 generations"));
    let message = payload["message"].as_str().unwrap();
    assert!(message.contains("stubbed"));
    assert!(message.contains(summary));
    assert!(payload["file_name"].as_str().unwrap().ends_with(".gif"));
    assert!(
        payload["last_gif_path"]
            .as_str()
            .unwrap()
            .ends_with("gif/last.gif")
    );
    assert!(last_path.exists());

    let get_request = Request::builder()
        .method("GET")
        .uri("/last.gif")
        .body(Body::empty())
        .unwrap();
    let gif_response = app.oneshot(get_request).await.unwrap();
    assert_eq!(gif_response.status(), StatusCode::OK);

    if let Some(bytes) = previous {
        fs::create_dir_all(last_path.parent().unwrap()).unwrap();
        fs::write(&last_path, bytes).unwrap();
    } else {
        let _ = fs::remove_file(&last_path);
    }
}

async fn telegram_send_animation_stub(req: Request<Body>) -> impl IntoResponse {
    let (parts, body) = req.into_parts();
    assert_eq!(parts.uri.path(), "/botTESTTOKEN/sendAnimation");
    let bytes = body::to_bytes(body, 1_000_000).await.unwrap();
    let body_text = String::from_utf8_lossy(&bytes);
    assert!(body_text.contains("caption"));
    assert!(body_text.contains("Simulated 2 generations"));
    assert!(body_text.contains("stubbed"));
    Json(json!({
        "ok": true,
        "result": {
            "message_id": 1,
            "date": 0,
            "chat": {
                "id": 1,
                "type": "channel",
                "title": "stub"
            }
        }
    }))
}

async fn telegram_get_me_stub(_req: Request<Body>) -> impl IntoResponse {
    Json(json!({
        "ok": true,
        "result": {
            "id": 1,
            "is_bot": true,
            "first_name": "Stub Bot"
        }
    }))
}
