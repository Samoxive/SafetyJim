package org.samoxive.safetyjim.database

import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import java.util.*

private const val createSQL =
    """
create table if not exists user_secrets (
    user_id      bigint not null primary key,
    access_token text not null,
    update_time  bigint not null
);
"""

private const val upsertSQL =
    """
insert into user_secrets (
    user_id, access_token, update_time
) values ($1, $2, $3)
on conflict (user_id) do update
set access_token = excluded.access_token, update_time = excluded.update_time; 
"""

object UserSecretsTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()

    private fun RowSet<Row>.toUserSecretsEntities(): List<UserSecretsEntity> = this.map {
        UserSecretsEntity(
            userId = it.getLong(0),
            accessToken = it.getString(1)
        )
    }

    suspend fun fetchUserSecrets(userId: Long): UserSecretsEntity? {
        return pgPool.preparedQueryAwait("select * from user_secrets where user_id = $1;", Tuple.of(userId))
            .toUserSecretsEntities()
            .firstOrNull()
    }

    suspend fun upsertUserSecrets(userSecrets: UserSecretsEntity) {
        pgPool.preparedQueryAwait(upsertSQL, Tuple.of(userSecrets.userId, userSecrets.accessToken, Date().time / 1000))
    }
}

data class UserSecretsEntity(
    val userId: Long,
    val accessToken: String
)
