import java.nio.file.*;
import java.sql.*;
import java.util.*;

class InstallAdminReviewSchema {
    static Set<String> columns(Connection c)throws SQLException{
        var result=new HashSet<String>();try(var rows=c.getMetaData().getColumns(c.getCatalog(),null,"stay_hotels",null)){while(rows.next())if(rows.getString("TABLE_NAME").equals("stay_hotels"))result.add(rows.getString("COLUMN_NAME"));}return result;
    }
    static Map<String,Long> statuses(Connection c,String table)throws SQLException{
        var result=new HashMap<String,Long>();try(var s=c.createStatement();var rows=s.executeQuery("select status,count(*) from "+table+" group by status")){while(rows.next())result.put(rows.getString(1),rows.getLong(2));}return result;
    }
    public static void main(String[] args)throws Exception{
        var env=new Properties();try(var reader=Files.newBufferedReader(Path.of(".env.local"))){env.load(reader);}
        try(var c=DriverManager.getConnection(env.getProperty("SPRING_DATASOURCE_URL"),env.getProperty("SPRING_DATASOURCE_USERNAME"),env.getProperty("SPRING_DATASOURCE_PASSWORD"))){
            if(c.getMetaData().getDatabaseMajorVersion()<8)throw new IllegalStateException("MySQL 8 required");
            var existing=columns(c);if(!existing.containsAll(Set.of("id","status","version")))throw new IllegalStateException("Hotel schema missing");
            var hotelStates=statuses(c,"stay_hotels");var bookingStates=statuses(c,"stay_bookings");
            var definitions=new LinkedHashMap<String,String>();definitions.put("review_status","VARCHAR(32) NOT NULL DEFAULT 'NOT_SUBMITTED'");definitions.put("review_note","VARCHAR(1000) NULL");definitions.put("submitted_at","DATETIME(6) NULL");definitions.put("reviewed_at","DATETIME(6) NULL");definitions.put("reviewed_by_id","BIGINT NULL");
            for(var entry:definitions.entrySet())if(!existing.contains(entry.getKey()))try(var s=c.createStatement()){s.execute("ALTER TABLE stay_hotels ADD COLUMN "+entry.getKey()+" "+entry.getValue());}
            try(var s=c.createStatement()){s.executeUpdate("UPDATE stay_hotels SET review_status='APPROVED' WHERE status='PUBLISHED' AND review_status='NOT_SUBMITTED'");}
            boolean indexed=false;try(var indexes=c.getMetaData().getIndexInfo(c.getCatalog(),null,"stay_hotels",false,false)){while(indexes.next())if("ix_stay_hotels_review".equals(indexes.getString("INDEX_NAME")))indexed=true;}
            if(!indexed)try(var s=c.createStatement()){s.execute("CREATE INDEX ix_stay_hotels_review ON stay_hotels(review_status,id)");}
            if(!columns(c).containsAll(definitions.keySet()))throw new IllegalStateException("Review column verification failed");
            if(!hotelStates.equals(statuses(c,"stay_hotels"))||!bookingStates.equals(statuses(c,"stay_bookings")))throw new IllegalStateException("Concurrent publication/booking change detected; inspect counts before continuing");
            System.out.println("Admin review columns and queue index verified. Existing published hotels retained.");
            System.out.println("Hotel publication and booking statuses/counts unchanged. No admin accounts created.");
        }
    }
}
