package org.samoxive.safetyjim.database

import io.reactiverse.kotlin.pgclient.preparedQueryAwait
import io.reactiverse.pgclient.Tuple
import java.util.*

private const val createSQL = """
create table if not exists uuidblacklist (
    id uuid not null primary key
);
"""

object UUIDBlacklistTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()

    suspend fun isUUIDBlacklisted(id: UUID): Boolean {
        return pgPool.preparedQueryAwait("select * from uuidblacklist where id = $1;", Tuple.of(id))
                .any()
    }
}
