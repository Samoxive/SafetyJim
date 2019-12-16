package org.samoxive.safetyjim.database

import io.reactiverse.kotlin.pgclient.preparedQueryAwait
import io.reactiverse.pgclient.PgRowSet
import io.reactiverse.pgclient.Tuple

private const val createSQL = """
create table if not exists usersecrets (
    userid bigint not null primary key,
    accesstoken text not null
);
"""

private const val upsertSQL = """
insert into usersecrets (
    userid, accesstoken
) values ($1, $2) on conflict (userid)
do update set accesstoken = excluded.accesstoken; 
"""

object UserSecretsTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()

    private fun PgRowSet.toUserSecretsEntities(): List<UserSecretsEntity> = this.map {
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
        pgPool.preparedQueryAwait(upsertSQL, userSecrets.toTuple())
    }
}

data class UserSecretsEntity(
    val userId: Long,
    val accessToken: String
) {
    fun toTuple(): Tuple {
        return Tuple.of(userId, accessToken)
    }
}
