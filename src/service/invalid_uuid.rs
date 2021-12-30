use tracing::error;
use typemap_rev::TypeMapKey;

use crate::database::invalid_uuids::InvalidUUIDsRepository;

impl TypeMapKey for InvalidUUIDService {
    type Value = InvalidUUIDService;
}

pub struct InvalidUUIDService {
    pub repository: InvalidUUIDsRepository,
}

impl InvalidUUIDService {
    pub async fn is_uuid_invalid(&self, uuid: uuid::Uuid) -> bool {
        self.repository
            .is_uuid_invalid(sqlx::types::Uuid::from_u128(uuid.as_u128()))
            .await
            .map_err(|err| {
                error!("failed to check uuid validity {:?}", err);
                err
            })
            .ok()
            .unwrap_or(false)
    }
}
