import java.nio.file.*;
import java.sql.*;
import java.util.*;

// Only creates the refund table; never modifies existing booking/payment rows.
class InstallRefundSchema {
    static Set<String> columns(Connection c,String table)throws SQLException{
        var found=new HashSet<String>();try(var rows=c.getMetaData().getColumns(c.getCatalog(),null,table,null)){
            while(rows.next())if(table.equals(rows.getString("TABLE_NAME")))found.add(rows.getString("COLUMN_NAME"));
        }return found;
    }
    static void unique(Connection c,String table,Set<String> columns)throws SQLException{
        var indexes=new HashMap<String,Set<String>>();try(var rows=c.getMetaData().getIndexInfo(c.getCatalog(),null,table,true,false)){
            while(rows.next()){var index=rows.getString("INDEX_NAME");var column=rows.getString("COLUMN_NAME");if(index!=null&&column!=null)indexes.computeIfAbsent(index,k->new HashSet<>()).add(column);}
        }if(!indexes.containsValue(columns))throw new IllegalStateException("Missing refund uniqueness constraint");
    }
    public static void main(String[] args)throws Exception{
        Properties env=new Properties();try(var reader=Files.newBufferedReader(Path.of(".env.local"))){env.load(reader);}
        try(var c=DriverManager.getConnection(env.getProperty("SPRING_DATASOURCE_URL"),env.getProperty("SPRING_DATASOURCE_USERNAME"),env.getProperty("SPRING_DATASOURCE_PASSWORD"))){
            if(c.getMetaData().getDatabaseMajorVersion()<8)throw new IllegalStateException("MySQL 8 required");
            if(!columns(c,"stay_bookings").containsAll(Set.of("id","payment_status"))||!columns(c,"stay_payment_attempts").contains("payment_intent_id"))throw new IllegalStateException("Payment schema must be installed first");
            var expected=Set.of("id","booking_id","request_key","payment_intent_id","amount_minor","currency","status","refund_id","policy_version","created_at","updated_at");
            var existing=columns(c,"stay_refund_attempts");if(!existing.isEmpty()&&!existing.equals(expected))throw new IllegalStateException("Unexpected existing refund columns; no migration was attempted");
            String sql=Files.readString(Path.of(args.length==0?"src/main/resources/db/manual/008_refunds.sql":args[0])).replaceAll("(?m)^--.*$","").strip();
            if(!sql.startsWith("CREATE TABLE IF NOT EXISTS stay_refund_attempts")||sql.substring(0,sql.length()-1).contains(";"))throw new IllegalStateException("Only the guarded refund CREATE TABLE is allowed");
            try(var s=c.createStatement()){s.execute(sql);}
            if(!columns(c,"stay_refund_attempts").equals(expected))throw new IllegalStateException("Refund schema verification failed");
            unique(c,"stay_refund_attempts",Set.of("booking_id"));unique(c,"stay_refund_attempts",Set.of("request_key"));unique(c,"stay_refund_attempts",Set.of("refund_id"));
            boolean foreignKey=false;try(var keys=c.getMetaData().getImportedKeys(c.getCatalog(),null,"stay_refund_attempts")){
                while(keys.next())if(keys.getString("PKTABLE_NAME").equals("stay_bookings")&&keys.getString("FKCOLUMN_NAME").equals("booking_id"))foreignKey=true;
            }if(!foreignKey)throw new IllegalStateException("Refund booking foreign key is missing");
            System.out.println("Refund table, columns, unique keys and booking foreign key verified.");
            System.out.println("Existing bookings/payments were not modified. No refund requests were submitted.");
        }
    }
}
