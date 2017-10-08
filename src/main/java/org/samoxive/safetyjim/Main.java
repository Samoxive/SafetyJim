package org.samoxive.safetyjim;

import jooq.generated.Tables;
import jooq.generated.tables.records.BanlistRecord;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.DriverManager;

public class Main {
    public static void main(String ...args) {
        String userName = "postgres";
        String password = "postgres";
        String url = "jdbc:postgresql://localhost:5432/dev";

        try (Connection conn = DriverManager.getConnection(url, userName, password)) {
            DSLContext create = DSL.using(conn, SQLDialect.POSTGRES);
            BanlistRecord record = create.newRecord(Tables.BANLIST);
            record.setExpires(true);
            record.store();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
