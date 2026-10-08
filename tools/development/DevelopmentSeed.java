package development;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Explicit one-shot development tool. Never registered with Spring or Flyway. */
public final class DevelopmentSeed {
    static final String URL = "jdbc:mysql://127.0.0.1:3306/hpoa_dev";
    record Row(String table, LinkedHashMap<String,String> values) {}
    static List<Row> parse(String sql) {
        List<Row> rows = new ArrayList<>();
        Matcher insert = Pattern.compile("INSERT INTO `([^`]+)` \\(([^)]+)\\) VALUES\\s*(.*?);", Pattern.DOTALL).matcher(sql);
        while (insert.find()) {
            String[] columns = insert.group(2).replace("`", "").split(",\\s*");
            Matcher tuple = Pattern.compile("\\(([^()]*)\\)").matcher(insert.group(3));
            while (tuple.find()) {
                String[] values = tuple.group(1).split(",(?=(?:[^']*'[^']*')*[^']*$)\\s*");
                if (columns.length != values.length) throw new IllegalStateException("Malformed fixture");
                var map = new LinkedHashMap<String,String>();
                for (int i=0;i<columns.length;i++) {
                    String v=values[i].trim();
                    map.put(columns[i], v.startsWith("'") ? v.substring(1,v.length()-1) : v);
                }
                rows.add(new Row(insert.group(1), map));
            }
        }
        // Preserve the verified fixture's employee-based department manager links.
        for (Row row : rows) if (row.table.equals("t_dept"))
            row.values.put("manager_id", row.values.get("id").equals("1") ? "3" : "6");
        if (rows.size()!=58) throw new IllegalStateException("Unexpected fixture inventory");
        return rows;
    }
    static void target(String url, String user) {
        if (!URL.equals(url) || !"hpoa_dev".equals(user)) throw new IllegalStateException("Unexpected development target");
    }
    static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    static Object value(String raw, Map<String,String> hashes, Timestamp time) {
        if (raw.equals("NULL")) return null;
        if (raw.equals("@seed_time")) return time;
        if (raw.endsWith("_hash") && raw.startsWith("@")) return hashes.get(raw);
        return raw;
    }
    static boolean same(String expected, Object actual) {
        return expected.equals("NULL") ? actual==null : actual!=null && expected.equals(actual.toString());
    }
    // Require the complete current application migration sequence before any seed writes.
    static void validateMigrations(List<String> versions, List<Boolean> successes) {
        require(List.of("1", "2").equals(versions) && List.of(true, true).equals(successes),
                "Unexpected migration state");
    }
    static void selfTest(List<Row> rows) {
        target(URL,"hpoa_dev");
        for (String wrong : List.of("jdbc:mysql://127.0.0.1:3306/hp", "jdbc:mysql://127.0.0.1:3307/hpoa_dev")) {
            try { target(wrong,"hpoa_dev"); throw new AssertionError("Unsafe target accepted"); } catch (IllegalStateException expected) { }
        }
        try { target(URL,"root"); throw new AssertionError("Root accepted"); } catch (IllegalStateException expected) { }
        require(same("NULL",null) && same("1",1) && !same("1",2),"Comparison regression");
        require(com.ada.approval.workflow.WorkflowTitleNames.equivalent("General Manager",com.ada.approval.workflow.WorkflowTitleNames.generalManagerNames().get(1))
            && com.ada.approval.workflow.WorkflowTitleNames.equivalent("HR",com.ada.approval.workflow.WorkflowTitleNames.hrNames().get(1))
            && !com.ada.approval.workflow.WorkflowTitleNames.equivalent("HR","Employee"),"Historical title compatibility regression");
        require(rows.stream().filter(r->r.table.equals("t_account")).count()==6,"Account inventory");
        require(rows.stream().filter(r->r.table.equals("t_permission")).count()==13,"Grant inventory");
        require(rows.stream().noneMatch(r->r.table.startsWith("ACT_") || r.table.equals("t_holiday_apply") || r.table.equals("t_holiday_approval")),"Unexpected seeded workflow data");
        var encoder = new BCryptPasswordEncoder(12);
        String hash = encoder.encode("database-free-test-password");
        require(encoder.matches("database-free-test-password",hash),"BCrypt regression");
        System.out.println("DEV_SEED_DATABASE_FREE_PASS");
    }
    static void validateRotationPassword(String password) throws Exception {
        Class.forName("com.ada.approval.config.security.PasswordPolicy").getMethod("validate",String.class).invoke(null,password);
    }
    static void rotate() throws Exception {
        target(System.getenv("DEV_SEED_URL"),System.getenv("DEV_SEED_USER"));
        String password=System.getenv("HPOA_DEMO_PASSWORD");
        String oldPassword=System.getenv("DEV_DEMO_OLD_PASSWORD");
        validateRotationPassword(password);
        require(oldPassword!=null && !oldPassword.equals(password),"New credential required");
        var encoder=(org.springframework.security.crypto.password.PasswordEncoder)
            Class.forName("com.ada.approval.config.security.SecurityConfig").getMethod("encoder").invoke(null);
        Map<String,Integer> identities=Map.of("employee.a@example.test",1,"employee.b@example.test",2,
            "manager@example.test",3,"general.manager@example.test",4,"hr@example.test",5,"admin@example.test",6);
        try(Connection db=DriverManager.getConnection(URL,"hpoa_dev",System.getenv("DEV_SEED_DB_PASSWORD"))) {
            try(Statement st=db.createStatement();ResultSet rs=st.executeQuery("SELECT @@port,DATABASE(),CURRENT_USER()")) {
                require(rs.next() && rs.getInt(1)==3306 && rs.getString(2).equals("hpoa_dev") && rs.getString(3).equals("hpoa_dev@127.0.0.1"),"Identity mismatch");
            }
            try(Statement st=db.createStatement();ResultSet rs=st.executeQuery("SELECT GET_LOCK('hpoa_dev_development_seed',0)")) {
                require(rs.next() && rs.getInt(1)==1,"Concurrent credential operation");
            }
            db.setAutoCommit(false);
            try {
                Map<Integer,String> replacement=new LinkedHashMap<>();
                for(var identity:identities.entrySet()) {
                    try(PreparedStatement st=db.prepareStatement("SELECT id,emp_id,status,password FROM t_account WHERE user_name=? FOR UPDATE")) {
                        st.setString(1,identity.getKey());
                        try(ResultSet rs=st.executeQuery()) {
                            require(rs.next(),"Demo account missing");
                            int id=rs.getInt("id");
                            require(id==identity.getValue()+10 && rs.getInt("emp_id")==identity.getValue() && rs.getInt("status")==1,"Demo identity conflict");
                            require(encoder.matches(oldPassword,rs.getString("password")),"Local demo credential conflict");
                            require(!rs.next(),"Duplicate demo identity");
                            replacement.put(id,encoder.encode(password));
                        }
                    }
                }
                require(replacement.size()==6,"Unexpected account count");
                for(var account:replacement.entrySet()) {
                    try(PreparedStatement st=db.prepareStatement("UPDATE t_account SET password=? WHERE id=?")) {
                        st.setString(1,account.getValue());st.setInt(2,account.getKey());
                        require(st.executeUpdate()==1,"Update count mismatch");
                    }
                }
                for(int id:replacement.keySet()) {
                    try(PreparedStatement st=db.prepareStatement("SELECT password FROM t_account WHERE id=?")) {
                        st.setInt(1,id);
                        try(ResultSet rs=st.executeQuery()) { require(rs.next() && encoder.matches(password,rs.getString(1)),"Credential verification failure"); }
                    }
                }
                db.commit();
                System.out.println("DEV_PASSWORD_ROTATION_PASS verified_accounts=6 password_columns_only=true");
            } catch(Exception failure) { db.rollback();throw failure; }
        }
    }
    public static void main(String[] args) throws Exception {
        if (args[0].equals("--rotate-password")) { rotate(); return; }
        List<Row> rows=parse(Files.readString(Path.of(args[1])));
        if (args[0].equals("--self-test")) { selfTest(rows); return; }
        require(args[0].equals("--seed"),"Explicit --seed required");
        target(System.getenv("DEV_SEED_URL"),System.getenv("DEV_SEED_USER"));
        String password=System.getenv("HPOA_DEMO_PASSWORD");
        require(password!=null && password.length()>=16,"Demo password must contain at least 16 characters");
        var encoder=new BCryptPasswordEncoder(12);
        var hashes=new HashMap<String,String>();
        for (Row row:rows) if (row.table.equals("t_account")) hashes.put(row.values.get("password"),encoder.encode(password));
        try (Connection db=DriverManager.getConnection(URL,"hpoa_dev",System.getenv("DEV_SEED_DB_PASSWORD"))) {
            try (Statement st=db.createStatement(); ResultSet rs=st.executeQuery("SELECT @@port,DATABASE(),CURRENT_USER()")) {
                require(rs.next() && rs.getInt(1)==3306 && rs.getString(2).equals("hpoa_dev") && rs.getString(3).equals("hpoa_dev@127.0.0.1"),"Server identity mismatch");
            }
            try (Statement st=db.createStatement(); ResultSet rs=st.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND LEFT(TABLE_NAME,2)='t_'")) {
                require(rs.next() && rs.getInt(1)==12,"Unexpected application schema");
            }
            try (Statement st=db.createStatement(); ResultSet rs=st.executeQuery("SELECT version,success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank")) {
                List<String> versions = new ArrayList<>();
                List<Boolean> successes = new ArrayList<>();
                while (rs.next()) {
                    versions.add(rs.getString(1));
                    successes.add(rs.getBoolean(2));
                }
                validateMigrations(versions, successes);
            }
            try (Statement st=db.createStatement(); ResultSet rs=st.executeQuery("SELECT VALUE_ FROM ACT_GE_PROPERTY WHERE NAME_='schema.version'")) {
                require(rs.next() && rs.getString(1).equals("8.1.0"),"Unexpected engine schema");
            }
            try (PreparedStatement lock=db.prepareStatement("SELECT GET_LOCK('hpoa_dev_development_seed',0)" ); ResultSet rs=lock.executeQuery()) {
                require(rs.next() && rs.getInt(1)==1,"Concurrent seeder detected");
            }
            db.setAutoCommit(false);
            int inserted=0, retained=0;
            try {
                // Read/validate ALL existing fixture records before the first INSERT.
                List<Row> missing=new ArrayList<>();
                Map<String,Set<String>> ids=new HashMap<>();
                for(Row row:rows) ids.computeIfAbsent(row.table,k->new HashSet<>()).add(row.values.get("id"));
                for(var entry:ids.entrySet()) {
                    try(Statement st=db.createStatement(); ResultSet rs=st.executeQuery("SELECT id FROM `"+entry.getKey()+"` FOR UPDATE")) {
                        while(rs.next()) require(entry.getValue().contains(rs.getString(1)),"Non-fixture record conflict in "+entry.getKey());
                    }
                }
                for(Row row:rows) {
                    try(PreparedStatement query=db.prepareStatement("SELECT * FROM `"+row.table+"` WHERE id=? FOR UPDATE")) {
                        query.setString(1,row.values.get("id"));
                        try(ResultSet rs=query.executeQuery()) {
                            if(!rs.next()) { missing.add(row); continue; }
                            for(var field:row.values.entrySet()) {
                                if(field.getKey().equals("create_time") || field.getKey().equals("update_time")) continue;
                                if(field.getKey().equals("password")) {
                                    require(encoder.matches(password,rs.getString("password")),"Existing demo credential conflict");
                                } else require((field.getKey().equals("title_name") ? com.ada.approval.workflow.WorkflowTitleNames.equivalent(field.getValue(),rs.getString(field.getKey())) : same(field.getValue(),rs.getObject(field.getKey()))),"Fixture conflict in "+row.table+" id="+row.values.get("id"));
                            }
                            retained++;
                        }
                    }
                }
                Timestamp time=new Timestamp(System.currentTimeMillis());
                for(Row row:missing) {
                    String columns=String.join(",",row.values.keySet().stream().map(k->"`"+k+"`").toList());
                    String placeholders=String.join(",",Collections.nCopies(row.values.size(),"?"));
                    try(PreparedStatement insert=db.prepareStatement("INSERT INTO `"+row.table+"` ("+columns+") VALUES ("+placeholders+")")) {
                        int i=1;for(String raw:row.values.values())insert.setObject(i++,value(raw,hashes,time));
                        require(insert.executeUpdate()==1,"Insert count mismatch");inserted++;
                    }
                }
                db.commit();
                System.out.println("DEV_SEED_PASS inserted="+inserted+" verified_existing="+retained);
            } catch(Exception failure) { db.rollback(); throw failure; }
        }
    }
}
