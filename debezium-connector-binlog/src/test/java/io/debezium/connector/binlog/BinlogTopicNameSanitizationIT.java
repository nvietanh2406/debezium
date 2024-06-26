/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.binlog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceConnector;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.debezium.config.CommonConnectorConfig;
import io.debezium.config.CommonConnectorConfig.SchemaNameAdjustmentMode;
import io.debezium.config.Configuration;
import io.debezium.connector.binlog.util.TestHelper;
import io.debezium.connector.binlog.util.UniqueDatabase;
import io.debezium.data.VerifyRecord;
import io.debezium.doc.FixFor;

/**
 * Tests around {@code NUMERIC} columns. Keep in sync with {@link BinlogDecimalColumnIT}.
 *
 * @author Gunnar Morling
 * @author Chris Cranford
 */
public abstract class BinlogTopicNameSanitizationIT<C extends SourceConnector> extends AbstractBinlogConnectorIT<C> {

    private static final Path SCHEMA_HISTORY_PATH = Files.createTestingPath("file-schema-history-topic-name-sanitization.txt")
            .toAbsolutePath();
    private final UniqueDatabase DATABASE = TestHelper.getUniqueDatabase("topic-name.sanitization-it", "topic_name_sanitization_test")
            .withDbHistoryPath(SCHEMA_HISTORY_PATH);

    private Configuration config;

    @Before
    public void beforeEach() {
        stopConnector();
        DATABASE.createAndInitialize();
        initializeConnectorTestFramework();
        Files.delete(SCHEMA_HISTORY_PATH);
    }

    @After
    public void afterEach() {
        try {
            stopConnector();
        }
        finally {
            Files.delete(SCHEMA_HISTORY_PATH);
        }
    }

    @Test
    @FixFor("DBZ-878")
    public void shouldReplaceInvalidTopicNameCharacters() throws SQLException, InterruptedException {
        // Use the DB configuration to define the connector's configuration ...
        config = DATABASE.defaultConfig()
                .with(BinlogConnectorConfig.SNAPSHOT_MODE, BinlogConnectorConfig.SnapshotMode.NEVER)
                .with(CommonConnectorConfig.SCHEMA_NAME_ADJUSTMENT_MODE, SchemaNameAdjustmentMode.AVRO)
                .build();

        // Start the connector ...
        start(getConnectorClass(), config);

        // ---------------------------------------------------------------------------------------------------------------
        // Consume all of the events due to startup and initialization of the database
        // ---------------------------------------------------------------------------------------------------------------
        // Testing.Debug.enable();
        int numCreateDatabase = 1;
        int numCreateTables = 2;
        int numInserts = 2;
        SourceRecords records = consumeRecordsByTopic(numCreateDatabase + numCreateTables + numInserts);
        stopConnector();
        assertThat(records).isNotNull();
        records.forEach(this::validate);

        List<SourceRecord> dmls = records.recordsForTopic(DATABASE.topicForTable("dbz_878_some_test_data"));
        assertThat(dmls).hasSize(1);

        SourceRecord insert = dmls.get(0);
        assertThat(insert.valueSchema().name()).endsWith("dbz_878_some_test_data.Envelope");
        VerifyRecord.isValidInsert(insert, "id", 1);

        String sourceTable = ((Struct) insert.value()).getStruct("source").getString("table");
        assertThat(sourceTable).isEqualTo("dbz_878_some|test@data");
    }

    @Test
    @FixFor("DBZ-1834")
    public void shouldAcceptDotInTableName() throws SQLException, InterruptedException {
        // Use the DB configuration to define the connector's configuration ...
        config = DATABASE.defaultConfig()
                .with(BinlogConnectorConfig.SNAPSHOT_MODE, BinlogConnectorConfig.SnapshotMode.NEVER)
                .with(CommonConnectorConfig.SCHEMA_NAME_ADJUSTMENT_MODE, SchemaNameAdjustmentMode.AVRO)
                .build();

        // Start the connector ...
        start(getConnectorClass(), config);

        // ---------------------------------------------------------------------------------------------------------------
        // Consume all of the events due to startup and initialization of the database
        // ---------------------------------------------------------------------------------------------------------------
        // Testing.Debug.enable();
        int numCreateDatabase = 1;
        int numCreateTables = 2;
        int numInserts = 2;
        SourceRecords records = consumeRecordsByTopic(numCreateDatabase + numCreateTables + numInserts);
        stopConnector();
        assertThat(records).isNotNull();
        records.forEach(this::validate);

        List<SourceRecord> dmls = records.recordsForTopic(DATABASE.topicForTable("DBZ.1834"));
        assertThat(dmls).hasSize(1);

        SourceRecord insert = dmls.get(0);
        assertThat(insert.valueSchema().name()).endsWith("DBZ.1834.Envelope");
        VerifyRecord.isValidInsert(insert, "id", 1);

        String sourceTable = ((Struct) insert.value()).getStruct("source").getString("table");
        assertThat(sourceTable).isEqualTo("DBZ.1834");
    }
}
