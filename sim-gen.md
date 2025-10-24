# Simulation Web Service

The simulator now runs as a web service. Start it with:

```sh
export TELEGRAM_BOT_TOKEN=your_bot_token
export TELEGRAM_CHAT_ID=@your_channel_or_chat_id
# Optional: APP_BIND_ADDR or PORT to change 0.0.0.0:3000
cargo run
```

Send simulations via HTTP:

```sh
curl -X POST http://localhost:3000/generate \
  -H 'content-type: application/json' \
  -d '{
        "rule": "B3/S23",
        "steps": 50,
        "density": 0.08,
        "width": 200,
        "height": 200,
        "scale": 3,
        "caption": "Game of Life demo"
      }'
```

- `rule` uses the standard `B#/S#` syntax.
- Provide `density` and `init_mask` (3×3 binary string) to seed repeatable shapes; add `seed_cells` for explicit coordinates.
- GIFs are generated in-memory, sent to the configured Telegram channel, and the JSON response includes the derived filename plus simulation stats.
- On startup—and every day at 10:00 and 18:00 Asia/Tbilisi—the service automatically simulates rule `B3_S12345` and posts the resulting GIF plus a caption detailing the simulation parameters to the configured channel.

Run `cargo test` to exercise the simulator logic and the HTTP endpoint with a stubbed Telegram server.
