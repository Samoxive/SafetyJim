package org.samoxive.safetyjim.database

import io.vertx.sqlclient.Tuple
import java.util.*

private const val createSQL =
    """
create table if not exists invalid_uuids (
    id uuid not null primary key
);
"""

object UUIDBlocklistTable : AbstractTable {
    override val createStatement = createSQL
    override val createIndexStatements = arrayOf<String>()

    suspend fun isUUIDBlocklisted(id: UUID): Boolean {
        return pgPool.preparedQueryAwait("select * from invalid_uuids where id = $1;", Tuple.of(id))
            .any()
    }
}
