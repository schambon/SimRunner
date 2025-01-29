package org.schambon.loadsimrunner.runner.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import org.bson.Document;
import org.schambon.loadsimrunner.WorkloadManager;
import org.schambon.loadsimrunner.report.Reporter;
import org.schambon.loadsimrunner.runner.AbstractRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JDBCRunner extends AbstractRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(JDBCRunner.class);

    private Connection connection;

    public JDBCRunner(WorkloadManager config, Reporter reporter) {
        super(config, reporter);


        var connectionString = params.getString("connectionString");
        try {
            this.connection = DriverManager.getConnection(connectionString);
        } catch (SQLException sqle) {
            LOGGER.error("Caught exception while initializing JDBC connection", sqle);
            throw new RuntimeException(sqle);
        }
        
    }

    @SuppressWarnings("unchecked")
    @Override
    protected long doRun() {
        try {

            var start = System.currentTimeMillis();
            int count = 0;
            var statements = params.getList("statements", Document.class);
            for (var statement: statements) {
                if (statement.containsKey("sql")) {
                    count = _runStatement(statement);
                } else if (statement.containsKey("expr")) {
                    var expr = template.generateExpression(statement.get("expr"));
                    if (expr instanceof Document) {
                        count = _runStatement((Document) expr);
                    } else if (expr instanceof List<?>) {
                        // we assume expr compiles to an array of documents
                        var lexpr = (List<Document>)expr;
                        for (var x : lexpr) {
                            count = _runStatement(x);
                        }
                    }
                }
                
            }

            var duration = System.currentTimeMillis() - start;
            reporter.reportOp(name, count, duration);
            return duration;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private int _runStatement(Document statement) throws SQLException {
        int count;
        try (var ps = connection.prepareStatement(statement.getString("sql"))) {
            if (statement.containsKey("params")) {
                var statementParams = statement.getList("params", Object.class);
                for (var i = 1; i <= statementParams.size(); i++) {
                    ps.setObject(i, template.generateExpression(statementParams.get(i-1)));
                }
            }
            
            var hasResult = ps.execute();
            if (hasResult) {
                count = 0;
                var last = new Document();
                var resultset = ps.getResultSet();

                while (resultset.next()) {

                    for (var i = 1; i <= resultset.getMetaData().getColumnCount(); i++) {
                        last.put(resultset.getMetaData().getColumnName(i), resultset.getObject(i));
                    }

                    count++;
                }

                var bindName = statement.getString("bind");
                if (bindName != null)
                    template.getLocalVariables().put(bindName, last); // local variables are thread-local so we *SHOULD* be fine
            } else {
                count = 1; // TODO account for bulk / multi writes
            }
        }
        return count;
    }



}
