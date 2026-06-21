package io.github.openflowkernel.jdbc.persistence;

import io.github.openflowkernel.persistence.contract.PersistenceRepositories;
import io.github.openflowkernel.persistence.contract.PersistenceRepositoryContract;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;

import javax.sql.DataSource;

class JdbcPersistenceContractTest implements PersistenceRepositoryContract {
    private PersistenceRepositories repositories;

    @BeforeEach
    void setUp() {
        DataSource dataSource = dataSource();
        new JdbcSchemaInitializer(dataSource).initialize();
        repositories = new PersistenceRepositories(
            new JdbcProcessDefinitionRepository(dataSource),
            new JdbcProcessInstanceRepository(dataSource),
            new JdbcTaskInstanceRepository(dataSource),
            new JdbcTaskRelationRepository(dataSource),
            new JdbcTaskCandidateRepository(dataSource)
        );
    }

    @Override
    public PersistenceRepositories repositories() {
        return repositories;
    }

    private static DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setUrl(
            "jdbc:h2:mem:" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
        );
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
