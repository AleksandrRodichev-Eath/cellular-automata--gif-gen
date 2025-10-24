use anyhow::{Context, Result, bail};
use reqwest::Client;
use reqwest::multipart::{Form, Part};
use serde::Deserialize;

#[derive(Clone)]
pub struct TelegramSender {
    client: Client,
    base_url: String,
    bot_token: String,
    chat_id: String,
}

impl TelegramSender {
    pub fn new(client: Client, bot_token: String, chat_id: String) -> Self {
        Self::with_base_url(
            client,
            "https://api.telegram.org".to_owned(),
            bot_token,
            chat_id,
        )
    }

    pub fn with_base_url(
        client: Client,
        base_url: String,
        bot_token: String,
        chat_id: String,
    ) -> Self {
        Self {
            client,
            base_url,
            bot_token,
            chat_id,
        }
    }

    pub async fn send_document(&self, file_name: &str, bytes: &[u8], caption: &str) -> Result<()> {
        let url = format!("{}/bot{}/sendDocument", self.base_url, self.bot_token);
        let mut form = Form::new()
            .text("chat_id", self.chat_id.clone())
            .text("caption", caption.to_owned());
        let part = Part::bytes(bytes.to_vec())
            .file_name(file_name.to_owned())
            .mime_str("image/gif")
            .context("failed to set GIF mime type")?;
        form = form.part("document", part);

        let response = self
            .client
            .post(url)
            .multipart(form)
            .send()
            .await
            .context("failed to call Telegram API")?;

        let status = response.status();
        let bytes = response
            .bytes()
            .await
            .context("failed to read Telegram API response body")?;
        let api_response: TelegramApiResponse =
            serde_json::from_slice(&bytes).context("failed to parse Telegram API response")?;

        if !status.is_success() || !api_response.ok {
            let description = api_response
                .description
                .unwrap_or_else(|| "unknown Telegram error".to_owned());
            bail!(
                "telegram API error (status {}): {}",
                status.as_u16(),
                description
            );
        }

        Ok(())
    }
}

#[derive(Debug, Deserialize)]
struct TelegramApiResponse {
    ok: bool,
    description: Option<String>,
}
