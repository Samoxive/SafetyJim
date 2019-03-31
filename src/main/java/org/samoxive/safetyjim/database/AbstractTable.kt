package org.samoxive.safetyjim.database

interface AbstractTable {
    abstract val createStatement: String
    abstract val createIndexStatements: Array<String>
}