import java.nio.file.*;
import java.util.*;
import java.sql.*;

// Run from the backend directory with the MySQL JDBC driver on the Java classpath.
class InstallPaymentSchema {
    static Set<String> columns(Connection c,String table)throws SQLException{var result=new HashSet<String>();try(var rows=c.getMetaData().getColumns(c.getCatalog(),null,table,null)){while(rows.next())if(table.equals(rows.getString("TABLE_NAME")))result.add(rows.getString("COLUMN_NAME"));}return result;}
    static Map<String,Long> statuses(Connection c)throws SQLException{var result=new HashMap<String,Long>();try(var s=c.createStatement();var rows=s.executeQuery("select status,count(*) from stay_bookings group by status")){while(rows.next())result.put(rows.getString(1),rows.getLong(2));}return result;}
    static void execute(Connection c,String sql)throws SQLException{try(var statement=c.createStatement()){statement.execute(sql);}}
    static void unique(Connection c,String table,Set<String> expected)throws SQLException{var indexes=new HashMap<String,Set<String>>();try(var rows=c.getMetaData().getIndexInfo(c.getCatalog(),null,table,true,false)){while(rows.next()){String name=rows.getString("INDEX_NAME"),col=rows.getString("COLUMN_NAME");if(name!=null&&col!=null)indexes.computeIfAbsent(name,k->new HashSet<>()).add(col);}}if(!indexes.containsValue(expected))throw new IllegalStateException("Missing payment uniqueness constraint");}
    public static void main(String[] args)throws Exception{
        Properties env=new Properties();try(var reader=Files.newBufferedReader(Path.of(".env.local"))){env.load(reader);}
        try(var c=DriverManager.getConnection(env.getProperty("SPRING_DATASOURCE_URL"),env.getProperty("SPRING_DATASOURCE_USERNAME"),env.getProperty("SPRING_DATASOURCE_PASSWORD"))){
            if(c.getMetaData().getDatabaseMajorVersion()<8)throw new IllegalStateException("MySQL 8 required");
            Map<String,Set<String>> tables=Map.of(
                "stay_payment_attempts",Set.of("id","booking_id","attempt_key","amount_minor","currency","status","session_id","session_url","payment_intent_id","requested_expires_at","cancellation_requested","created_at","paid_at"),
                "stay_payment_events",Set.of("id","session_id","processed_at"));
            for(var entry:tables.entrySet()){var existing=columns(c,entry.getKey());if(!existing.isEmpty()&&!existing.equals(entry.getValue()))throw new IllegalStateException("Unexpected existing payment table columns");}
            var bookingColumns=columns(c,"stay_bookings");if(!bookingColumns.containsAll(Set.of("id","status","quantity","guests","total","check_in","check_out")))throw new IllegalStateException("Booking schema is not ready");
            String check=null;try(var q=c.prepareStatement("select cc.CHECK_CLAUSE from information_schema.CHECK_CONSTRAINTS cc join information_schema.TABLE_CONSTRAINTS tc on tc.CONSTRAINT_SCHEMA=cc.CONSTRAINT_SCHEMA and tc.CONSTRAINT_NAME=cc.CONSTRAINT_NAME where tc.TABLE_SCHEMA=database() and tc.TABLE_NAME='stay_bookings' and tc.CONSTRAINT_NAME='ck_stay_booking_values'")){try(var rows=q.executeQuery()){if(rows.next())check=rows.getString(1);}}
            if(check==null||!check.contains("CONFIRMED")||!check.contains("CANCELLED"))throw new IllegalStateException("Expected booking CHECK is missing; inspect schema before migrating");
            var before=statuses(c);
            var sql=Files.readString(Path.of(args.length==0?"src/main/resources/db/manual/007_payments.sql":args[0])).replaceAll("(?m)^--.*$","");
            for(var part:sql.split(";")){String statement=part.strip();if(statement.isEmpty())continue;
                if(statement.startsWith("ALTER TABLE stay_bookings ADD COLUMN payment_status")){if(bookingColumns.contains("payment_status"))continue;}
                else if(statement.startsWith("ALTER TABLE stay_bookings ADD COLUMN expires_at")){if(bookingColumns.contains("expires_at"))continue;}
                else if(statement.startsWith("ALTER TABLE stay_bookings DROP CHECK ck_stay_booking_values")){if(check.contains("PENDING_PAYMENT")&&check.contains("EXPIRED"))continue;}
                else if(!statement.startsWith("CREATE TABLE IF NOT EXISTS stay_payment_"))throw new IllegalStateException("Unexpected migration statement");
                execute(c,statement);
            }
            if(!columns(c,"stay_bookings").containsAll(Set.of("payment_status","expires_at")))throw new IllegalStateException("Booking payment columns missing");
            for(var entry:tables.entrySet())if(!columns(c,entry.getKey()).equals(entry.getValue()))throw new IllegalStateException("Payment column verification failed");
            unique(c,"stay_payment_attempts",Set.of("booking_id"));unique(c,"stay_payment_attempts",Set.of("attempt_key"));unique(c,"stay_payment_attempts",Set.of("session_id"));
            try(var keys=c.getMetaData().getImportedKeys(c.getCatalog(),null,"stay_payment_attempts")){if(!keys.next())throw new IllegalStateException("Missing booking FK");}
            if(!before.equals(statuses(c)))throw new IllegalStateException("Booking statuses changed concurrently; inspect before continuing");
            System.out.println("Payment tables, unique keys and booking FK verified.");
            System.out.println("Existing booking counts/statuses preserved. No sample records or payments created.");
        }
    }
}
