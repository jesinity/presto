/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.tests.datatype;

import com.google.common.base.Joiner;
import io.prestosql.tests.sql.PrestoSqlExecutor;
import io.prestosql.tests.sql.SqlExecutor;
import io.prestosql.tests.sql.TestTable;

import java.util.List;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;

public class CreateAndPrestoInsertDataSetup
        implements DataSetup
{
    private final SqlExecutor sqlExecutor;
    private final PrestoSqlExecutor prestoSqlExecutor;
    private final String tableNamePrefix;

    public CreateAndPrestoInsertDataSetup(SqlExecutor sqlExecutor, PrestoSqlExecutor prestoSqlExecutor, String tableNamePrefix)
    {
        this.sqlExecutor = sqlExecutor;
        this.prestoSqlExecutor = prestoSqlExecutor;
        this.tableNamePrefix = tableNamePrefix;
    }

    @Override
    public TestTable setupTestTable(List<DataTypeTest.Input<?>> inputs)
    {
        TestTable testTable = createTestTable(inputs);
        try {
            insertRows(testTable, inputs);
        }
        catch (Exception e) {
            testTable.close();
            throw e;
        }
        return testTable;
    }

    private void insertRows(TestTable testTable, List<DataTypeTest.Input<?>> inputs)
    {
        Stream<String> literals = inputs.stream()
                .map(DataTypeTest.Input::toLiteral);
        String valueLiterals = Joiner.on(", ").join(literals.iterator());
        prestoSqlExecutor.execute(format("INSERT INTO %s VALUES(%s)", testTable.getName(), valueLiterals));
    }

    private TestTable createTestTable(List<DataTypeTest.Input<?>> inputs)
    {
        return new TestTable(sqlExecutor, tableNamePrefix, "(" + columnDefinitions(inputs) + ")");
    }

    private String columnDefinitions(List<DataTypeTest.Input<?>> inputs)
    {
        List<String> columnTypeDefinitions = inputs.stream()
                .map(DataTypeTest.Input::getInsertType)
                .collect(toList());
        Stream<String> columnDefinitions = range(0, columnTypeDefinitions.size())
                .mapToObj(i -> format("col_%d %s", i, columnTypeDefinitions.get(i)));
        return Joiner.on(",\n").join(columnDefinitions.iterator());
    }
}
