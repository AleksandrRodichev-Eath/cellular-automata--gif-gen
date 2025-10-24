use std::time::Duration as StdDuration;

use anyhow::{Context, Result};
use chrono::{Datelike, Duration, TimeZone, Utc};
use chrono_tz::Asia::Tbilisi;
use tokio::time::sleep;

use crate::generator::{self, SimulationOptions};
use crate::web::AppState;

const SCHEDULE_HOURS: [u32; 2] = [10, 18];
const SCHEDULE_RULE_CODE: &str = "B3/S12345";

pub async fn send_startup_snapshot(state: &AppState) -> Result<()> {
    send_scheduled_gif(state).await
}

pub fn spawn_daily_tasks(state: AppState) {
    tokio::spawn(async move {
        loop {
            let now = Utc::now();
            let next_run = next_scheduled_time(now);
            let sleep_duration = match (next_run - now).to_std() {
                Ok(duration) => duration,
                Err(_) => StdDuration::from_secs(0),
            };
            tracing::info!("Next scheduled GIF at {}", next_run);
            sleep(sleep_duration).await;
            if let Err(err) = send_scheduled_gif(&state).await {
                tracing::error!("Failed to send scheduled GIF: {err}");
            }
        }
    });
}

fn next_scheduled_time(now_utc: chrono::DateTime<Utc>) -> chrono::DateTime<Utc> {
    let local = now_utc.with_timezone(&Tbilisi);

    for &hour in &SCHEDULE_HOURS {
        if let Some(candidate) = Tbilisi
            .with_ymd_and_hms(local.year(), local.month(), local.day(), hour, 0, 0)
            .single()
        {
            if candidate > local {
                return candidate.with_timezone(&Utc);
            }
        }
    }

    let mut date = local.date_naive() + Duration::days(1);
    loop {
        if let Some(candidate) = Tbilisi
            .with_ymd_and_hms(
                date.year(),
                date.month(),
                date.day(),
                SCHEDULE_HOURS[0],
                0,
                0,
            )
            .single()
        {
            return candidate.with_timezone(&Utc);
        }
        date += Duration::days(1);
    }
}

async fn send_scheduled_gif(state: &AppState) -> Result<()> {
    let mut options = SimulationOptions::default();
    options.rule_label = SCHEDULE_RULE_CODE.to_owned();
    options.rule = SCHEDULE_RULE_CODE
        .parse()
        .context("failed to parse schedule rule")?;

    let result = generator::run_simulation(options)?;

    state
        .telegram
        .send_document(&result.file_name, &result.gif_bytes, &result.summary)
        .await?;
    tracing::info!("Dispatched scheduled GIF: {}", result.summary);
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::TimeZone;

    #[test]
    fn schedules_next_same_day_when_time_before_trigger() {
        let now = Utc.with_ymd_and_hms(2024, 12, 5, 5, 0, 0).single().unwrap();
        let next = next_scheduled_time(now);
        assert_eq!(
            next,
            Utc.with_ymd_and_hms(2024, 12, 5, 6, 0, 0).single().unwrap()
        ); // 10:00 Tbilisi == 06:00 UTC
    }

    #[test]
    fn schedules_next_next_day_when_after_last_trigger() {
        let now = Utc
            .with_ymd_and_hms(2024, 12, 5, 16, 30, 0)
            .single()
            .unwrap();
        let next = next_scheduled_time(now);
        assert_eq!(
            next,
            Utc.with_ymd_and_hms(2024, 12, 6, 6, 0, 0).single().unwrap()
        );
    }
}
