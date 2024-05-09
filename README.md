## BigQuery to Spanner using Apache Spark in Scala

### Why create this repo ? 

 * Google Cloud customers need to move data from BigQuery to Spanner using Apache Spark in Scala
 * Dataproc [templates](https://github.com/GoogleCloudPlatform/dataproc-templates) cannot help as they are written in Python & Java
 * The Apache Spark SQL [connector](https://github.com/GoogleCloudDataproc/spark-spanner-connector) for Google Cloud Spanner only supports reads not writes 

### Why is this a hard problem ? 

**1. data type mapping**

Based upon the [spark-bigquery-connector](https://github.com/GoogleCloudDataproc/spark-bigquery-connector?tab=readme-ov-file#data-types) docs & our own observations a type mapping is required.

| BigQuery GoogleSQL   | Apache Spark in Scala | Spanner GoogleSQL |
|----------|---------------|--------------|
| NUMERIC       | DecimalType         | FLOAT64        |
| BOOL | BooleanType       | TBD      |
 | DATE | DateType       | DATE      |
| DATETIME     | StringType          | TBD         |
| FLOAT64     | DoubleType        | TBD  |
| STRING     | StringType        | STRING(1024)  |
| BIGNUMERIC     | DecimalType        | TBD  |
| TIME     | LongType        | TBD  |
| TIMETSTAMP     | TimestampType        | TIMESTAMP  |
| INT64     | LongType        | INT64  |

**2. no jdbc dialect for spanner**

When using Apache Spark JDBC in Scala, there are pre-built SQL [dialects](https://github.com/apache/spark/tree/071feabbd4325504332679dfa620bc5ee4359370/sql/core/src/main/scala/org/apache/spark/sql/jdbc) for known databases:

* Teradata
* Postgres
* Oracle
* MySQL
* MsSqlServer
* H2
* DB2

However, there is no pre-built SQL dialect for Spanner.

When using Apache Spark JDBC in Scala to read or write with Spanner, the default JDBC SQL dialect is used which is incompatible with Spanner.

For this reason a new custom SQL dialect had to be written for Spanner, for use with Apache Spark JDBC in Scala.