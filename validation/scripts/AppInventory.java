import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;

import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Prints one tab-separated row per registered ApplicationSpec: id, supported auth modes on LocalStack,
 * runtimes, database requirement, and every engine:version in supportedDatabases(). Feeds compliance-matrix.py.
 * Run from cfc-testing: java -cp "target/dependency/*" scripts/AppInventory.java
 */
public class AppInventory {
    public static void main(String[] args) {
        TreeMap<String, String> rows = new TreeMap<>();
        for (ApplicationSpec spec : ServiceLoader.load(ApplicationSpec.class)) {
            String db = spec instanceof DatabaseSpec d ? String.valueOf(d.databaseRequirement().type()) : "NONE";
            String options = spec instanceof DatabaseSpec d && d.databaseRequirement().type()
                    != DatabaseSpec.DatabaseRequirement.RequirementType.NONE
                ? d.supportedDatabases().stream().map(o -> o.engine() + ":" + o.version())
                    .collect(Collectors.joining(","))
                : "";
            rows.put(spec.applicationId(), String.join("\t",
                String.join(",", spec.getSupportedAuthModes("localstack")),
                spec.supportsFargate() ? "FARGATE" : "-",
                spec.supportsEc2() ? "EC2" : "-",
                db, options));
        }
        System.out.println("applicationId\tauthModes\tfargate\tec2\tdatabase\tdatabaseOptions");
        rows.forEach((id, row) -> System.out.println(id + "\t" + row));
    }
}
