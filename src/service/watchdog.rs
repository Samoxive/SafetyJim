use std::process::exit;
use std::time::Instant;
use serenity::futures::lock::Mutex;
use tracing::error;
use typemap_rev::TypeMapKey;

const WATCHDOG_TIMER: u64 = 60 * 10;

impl TypeMapKey for WatchdogService {
    type Value = WatchdogService;
}

pub struct WatchdogService {
    feed_time: Mutex<Option<Instant>>,
}

impl WatchdogService {
    pub fn new() -> Self {
        WatchdogService {
            // Option used because we don't want unhealthy state until events start dispatching
            feed_time: Mutex::new(None),
        }
    }

    pub async fn feed(&self) {
        let mut lock = self.feed_time.lock().await;
        *lock = Some(Instant::now());
    }

    pub async fn is_healthy(&self) -> bool {
        let lock = self.feed_time.lock().await;
        let feed_time = match *lock {
            Some(time) => time,
            None => return true,
        };

        let now = Instant::now();
        let starvation_time = now.duration_since(feed_time).as_secs();
        if starvation_time < WATCHDOG_TIMER {
            true
        } else {
            error!("failed health check! haven't been fed for: {}s", starvation_time);
            // we could try a graceful shutdown by signaling the singleton Shutdown instance created in main
            // however this program's main job is processing Discord events, if we no longer receive them there
            // isn't much point trying to gracefully shutdown shards and finish database queries: restart and recover.
            exit(1);
        }
    }
}
