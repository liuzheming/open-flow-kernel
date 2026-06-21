package io.github.openflowkernel.jdbc.packet;

import io.github.openflowkernel.jdbc.persistence.JdbcSchemaInitializer;
import io.github.openflowkernel.jdbc.persistence.JdbcPacketDataRepo;
import io.github.openflowkernel.jdbc.persistence.JdbcPacketRepo;
import io.github.openflowkernel.packet.contract.PacketRepositories;
import io.github.openflowkernel.packet.contract.PacketRepositoryContract;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;

final class JdbcPacketRepositoryContractTest implements PacketRepositoryContract {
    @Override
    public PacketRepositories repositories() {
        DataSource dataSource = dataSource();
        new JdbcSchemaInitializer(dataSource).initialize();
        JdbcPacketDataRepo packetDataRepo = new JdbcPacketDataRepo(dataSource);
        return new PacketRepositories(
            new JdbcPacketRepo(dataSource, packetDataRepo),
            packetDataRepo
        );
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
