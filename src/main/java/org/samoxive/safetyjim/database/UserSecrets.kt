package org.samoxive.safetyjim.database

import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import java.util.*

private const val createSQL =
    """
create table if not exists usersecrets (
    userid bigint not null primary key,
    accesstoken text not null,
    updated bigint not null
);
"""

private const val upsertSQL =
    """
insert into usersecrets (
    userid, accesstoken, updated
) values ($1, $2, $3)
on conflict (userid) do update
set accesstoken = excluded.accesstoken, updated = excluded.updated; 
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
        return pgPool.preparedQueryAwait("select * from usersecrets where userid = $1;", Tuple.of(userId))
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
