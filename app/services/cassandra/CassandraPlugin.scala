/**
 *
 */
package services.cassandra

import com.netflix.astyanax.AstyanaxContext
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl
import com.netflix.astyanax.connectionpool.NodeDiscoveryType
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl
import com.netflix.astyanax.connectionpool.impl.CountingConnectionPoolMonitor
import com.netflix.astyanax.thrift.ThriftFamilyFactory
import play.api.{ Plugin, Logger, Application }
import com.netflix.astyanax.Keyspace
import com.netflix.astyanax.model.ColumnFamily
import com.netflix.astyanax.serializers.StringSerializer
import com.google.common.collect.ImmutableMap
import com.netflix.astyanax.util.RangeBuilder
import com.netflix.astyanax.model.ColumnList

/**
 * Setup Cassandra connection using Astyanax.
 *
 * @author Luigi Marini
 *
 */
class CassandraPlugin(application: Application) extends Plugin {

  var context: AstyanaxContext[Keyspace] = null // FIXME

  override def onStart() {
    Logger.debug("Starting Cassandra Plugin")
    context = new AstyanaxContext.Builder()
      .forCluster("Test Cluster")
      .forKeyspace("mykeyspace")
      .withAstyanaxConfiguration(new AstyanaxConfigurationImpl()
        //        .setCqlVersion("3.0.0")
        //        .setTargetCassandraVersion("1.2")
        .setCqlVersion("3.1.1")
        .setTargetCassandraVersion("2.0")
        .setDiscoveryType(NodeDiscoveryType.RING_DESCRIBE)
      )
      .withConnectionPoolConfiguration(new ConnectionPoolConfigurationImpl("MyConnectionPool")
        .setPort(9160)
        .setMaxConnsPerHost(1)
        .setSeeds("127.0.0.1:9160")
      )
      .withConnectionPoolMonitor(new CountingConnectionPoolMonitor())
      .buildKeyspace(ThriftFamilyFactory.getInstance());

    context.start();
    Logger.debug("Astyanax context started")
  }

  def getKeyspace() {
    context.getClient()
  }

  /**
   * For simple tests.
   */
  def test() {
    val keyspace = context.getClient()
    val CF_USER_INFO = new ColumnFamily[String, String](
      "users_3", // Column Family Name
      StringSerializer.get(), // Key Serializer
      StringSerializer.get()); // Column Serializer

    keyspace.createColumnFamily(CF_USER_INFO, ImmutableMap.builder[String, Object]()
      .put("default_validation_class", "UTF8Type")
      .put("key_validation_class", "UTF8Type")
      .put("comparator_type", "UTF8Type")
      .build());

    //    keyspace.createColumnFamily(CF_USER_INFO, null);

    // Inserting data
    var m = keyspace.prepareMutationBatch();

    m.withRow(CF_USER_INFO, "3")
      .putColumn("fname", "john", null)
      .putColumn("lname", "wayne", null)

    m.execute();

    //    keyspace
    //        .prepareQuery(CF_USER_INFO)
    //        .withCql("INSERT INTO users (user_id, fname, lname)  VALUES ('2', 'eran', 'landau');")
    //        .execute();

    import scala.collection.JavaConversions._

    val query = keyspace
      .prepareQuery(CF_USER_INFO)
      .getKey("user_id")
      .autoPaginate(true)
      .withColumnRange(new RangeBuilder().setLimit(10).build());
    var columns: ColumnList[String] = query.execute().getResult();
    while (!query.execute().getResult().isEmpty()) {
      for (c <- columns) {
        println(c.getName() + " " + c.getStringValue())
      }
    }

    //    val result =
    //      keyspace.prepareQuery(CF_USER_INFO)
    //        .getKey("1")
    //        .execute();
    //
    //    val columns2 = result.getResult();
    //    for (c <- columns2) {
    //      println(c.getName() + " " + c.getStringValue());
    //    }

    println("*************************")
  }

}