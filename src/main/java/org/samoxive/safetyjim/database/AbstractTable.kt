package org.samoxive.safetyjim.database

interface AbstractTable {
    val createStatement: String
    val createIndexStatements: Array<String>
}
