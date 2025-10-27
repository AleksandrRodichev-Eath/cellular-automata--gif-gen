use std::sync::Arc;

use anyhow::{Result, anyhow};
use telegram_bot_api::{
    bot::{BotApi, Error as BotError},
    methods,
    types::{self, ChatId},
};

#[derive(Clone)]
pub struct TelegramSender {
    api: Arc<BotApi>,
    chat_id: ChatId,
}

impl TelegramSender {
    pub async fn new(bot_token: String, chat_id: String) -> Result<Self> {
        Self::build_client(bot_token, chat_id, None).await
    }

    pub async fn with_base_url(
        base_url: String,
        bot_token: String,
        chat_id: String,
    ) -> Result<Self> {
        let api_url = format_custom_base_url(&base_url);
        Self::build_client(bot_token, chat_id, Some(api_url)).await
    }

    pub async fn send_animation(&self, file_name: &str, bytes: &[u8], caption: &str) -> Result<()> {
        let animation = types::InputFile::FileBytes(file_name.to_owned() + ".mp4", bytes.to_vec());
        let mut request = methods::SendAnimation::new(self.chat_id.clone(), animation);
        request.caption = Some(caption.to_owned());
        self.api
            .send_animation(request)
            .await
            .map_err(|err| map_bot_api_error(err, "failed to send animation to Telegram"))?;
        Ok(())
    }

    async fn build_client(
        bot_token: String,
        chat_id: String,
        api_url: Option<String>,
    ) -> Result<Self> {
        let api = BotApi::new(bot_token, api_url).await.map_err(|err| {
            map_bot_api_error(err, "failed to initialize Telegram Bot API client")
        })?;
        Ok(Self {
            api: Arc::new(api),
            chat_id: parse_chat_id(chat_id),
        })
    }
}

fn parse_chat_id(chat_id: String) -> ChatId {
    if let Ok(id) = chat_id.parse::<i64>() {
        ChatId::IntType(id)
    } else {
        ChatId::StringType(chat_id)
    }
}

fn format_custom_base_url(base_url: &str) -> String {
    let trimmed = base_url.trim_end_matches('/');
    if trimmed.ends_with("/bot") {
        trimmed.to_owned()
    } else {
        format!("{}/bot", trimmed)
    }
}

fn map_bot_api_error(err: Box<dyn std::error::Error>, context: &str) -> anyhow::Error {
    match err.downcast::<BotError>() {
        Ok(bot_err) => anyhow!("{context}: code {} - {}", bot_err.code, bot_err.message),
        Err(other) => anyhow!("{context}: {}", other),
    }
}
