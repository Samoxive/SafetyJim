use std::time::{SystemTime, UNIX_EPOCH};

use tokio::sync::broadcast;

#[derive(Clone)]
pub struct Shutdown {
    sender: broadcast::Sender<()>,
}

impl Shutdown {
    pub fn new() -> Self {
        let (sender, _) = broadcast::channel(1);

        Self { sender }
    }

    pub fn subscribe(&self) -> broadcast::Receiver<()> {
        self.sender.subscribe()
    }

    pub fn shutdown(&self) {
        let _ = self.sender.send(());
    }
}

pub fn now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("failed to get current time somehow")
        .as_secs()
}
