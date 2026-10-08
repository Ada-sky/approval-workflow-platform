package development;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Explicit one-shot development tool. Never registered with Spring or Flyway. */
public final class DockerDemoSeed {
    static final String URL = "jdbc:mysql://db:3306/hpoa_docker";
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
        if (!URL.equals(url) || !"hpoa_docker".equals(user)) throw new IllegalStateException("Unexpected development target");
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
    // Accept only the complete current migration sequence, including successful V2 archive support.
    static void validateMigrations(List<String> versions, List<Boolean> successes) {
        require(List.of("1", "2").equals(versions) && List.of(true, true).equals(successes),
                "Unexpected migration state");
    }
    static void selfTest(List<Row> rows) {
        target(URL,"hpoa_docker");
        for (String wrong : List.of("jdbc:mysql://127.0.0.1:3306/hp", "jdbc:mysql://127.0.0.1:3307/hpoa_docker")) {
            try { target(wrong,"hpoa_docker"); throw new AssertionError("Unsafe target accepted"); } catch (IllegalStateException expected) { }
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
        System.out.println("DOCKER_SEED_DATABASE_FREE_PASS");
    }
    public static void main(String[] args) throws Exception {
        require(args.length == 2, "Explicit operation and fixture required");
        List<Row> rows=parse(Files.readString(Path.of(args[1])));
        if (args[0].equals("--self-test")) { selfTest(rows); return; }
        require(args[0].equals("--seed"),"Explicit --seed required");
        target(System.getenv("DOCKER_SEED_URL"),System.getenv("DOCKER_SEED_USER"));
        String password=System.getenv("HPOA_DEMO_PASSWORD");
        com.ada.approval.config.security.PasswordPolicy.validate(password);
        require(password!=null && password.length()>=16,"Demo password must contain at least 16 characters");
        var encoder=new BCryptPasswordEncoder(12);
        var hashes=new HashMap<String,String>();
        for (Row row:rows) if (row.table.equals("t_account")) hashes.put(row.values.get("password"),encoder.encode(password));
        try (Connection db=DriverManager.getConnection(URL,"hpoa_docker",System.getenv("DOCKER_DB_PASSWORD"))) {
            try (Statement st=db.createStatement(); ResultSet rs=st.executeQuery("SELECT @@port,DATABASE(),CURRENT_USER()")) {
                require(rs.next() && rs.getInt(1)==3306 && rs.getString(2).equals("hpoa_docker") && rs.getString(3).equals("hpoa_docker@%"),"Server identity mismatch");
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
            try (PreparedStatement lock=db.prepareStatement("SELECT GET_LOCK('hpoa_docker_development_seed',0)" ); ResultSet rs=lock.executeQuery()) {
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
                System.out.println("DOCKER_SEED_PASS inserted="+inserted+" verified_existing="+retained);
            } catch(Exception failure) { db.rollback(); throw failure; }
        }
    }
}
