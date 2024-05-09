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

### How to build this code & use this template ?

1. [Dataproc2.2](https://cloud.google.com/dataproc/docs/concepts/versioning/dataproc-release-2.2)) images are the target execution environment
2. Given dataproc image, need Java JDK 11 
3. Given dataproc image, need Scala 2.12.18
4. Also need sbt
5. Build a JAR of this code via ```sbt package```
6. upload JAR to Google Cloud Storage
7. download & upload a JAR of the Spanner JDBC [driver](https://cloud.google.com/spanner/docs/jdbc-drivers)
8. Sumit a job to your dataproc cluster

```shell
gcloud dataproc jobs submit spark --cluster ${CLUSTER_NAME} \
    --region=us-central1 \
    --jar=${GCS_BUCKET_JARS}/${APP_JAR_NAME} \
    --jars=${GCS_BUCKET_JARS}/${SPANNER_JDBC_JAR} \
    -- ${PROJECT_ID} ${BQ_DATASET} ${BQ_TABLE} ${SPANNER_INSTANCE} ${SPANNER_DB} ${SPANNER_TABLE}
```

Where the arguments have this meaning

| cmd line arg   | meaning | example |
|----------|---------------|--------------|
| CLUSTER_NAME | TBD       | TTBD      |
 | GCS_BUCKET_JARS | TBD       | TBD      |
| APP_JAR_NAME     | TBD          | TBD         |
| SPANNER_JDBC_JAR     | TBD          | TBD         |
| PROJECT_ID     | TBD        | TBD  |
| BQ_DATASET     | TBD        | TBD  |
| BQ_TABLE     | TBD        | TBD  |
| SPANNER_INSTANCE     | TBD        | TBD  |
| SPANNER_DB     | TBD        | TBD  |
| SPANNER_TABLE     | TBD        | TBD  |

### Example of using this template

#### Example - part 1 of 4 - environment variables + setup

create some environment variables
```shell
export PROJECT_ID=$(gcloud config list core/project --format="value(core.project)")
export PROJECT_NUM=$(gcloud projects describe $PROJECT_ID --format="value(projectNumber)")
export GEO_REGION="US"
export GCS_BUCKET="gs://${PROJECT_ID}-gcpsparkscala"
export GCS_BUCKET_JARS="${GCS_BUCKET}/jars"
export BQ_DATASET="gcpsparkscala_demo_dataset"
export BQ_TABLE="demo"
export SPANNER_INSTANCE="gcpsparkscala-demo-instance"
export SPANNER_DB="gcpsparkscala-demo--db"
export SPANNER_TABLE="demo_data"
export CLUSTER_NAME="gcpsparkscala-demo-cluster"
export APP_JAR_NAME="gcpsparkscala.jar"

```
enable some apis
```shell
gcloud services enable dataproc.googleapis.com
```

#### Example - part 2 of 4 - BigQuery table (source)

make a dataset to house the table

```shell
bq --location=${GEO_REGION} mk \
--dataset \
${PROJECT_ID}:${BQ_DATASET}

```
make a table with schema to represent different [GoogleSQL data types](https://cloud.google.com/bigquery/docs/schemas#standard_sql_data_types)

```shell
bq mk \
 --table \
 --expiration 3600 \
 --description "This is a demo table for replication to spanner" \
 ${BQ_DATASET}.${BQ_TABLE} \
 id:INT64,measure1:FLOAT64,measure2:NUMERIC,dim1:BOOL,dim2:STRING
```

create some fake data 

```shell
bq query \
--append_table \
--use_legacy_sql=false \
--destination_table ${BQ_DATASET}.${BQ_TABLE} \
'SELECT
  CAST(2 AS INT64) AS id,
  CAST(6.28 AS FLOAT64) AS measure1,
  CAST(600 AS NUMERIC) AS measure2,
  FALSE AS dim1,
  "blabel" AS dim2'
```

#### Example - part 3 of 4 - Spanner table (sink)

create a spanner instance

```shell
gcloud spanner instances create ${SPANNER_INSTANCE} \
  --project=${PROJECT_ID}  \
  --config=regional-us-central1 \
  --description="Demo replication from BigQuery" \
  --nodes=1
```

create a database within the spanner instance (with dialect GoogleSQL)

```shell
gcloud spanner databases create ${SPANNER_DB} \
  --instance=${SPANNER_INSTANCE}
```

create a table in our Spanner DB, with schema matching BigQuery table
Spanner DDL uses GoogleSQL [data types](https://cloud.google.com/spanner/docs/reference/standard-sql/data-definition-language#data_types)


| column   | BigQuery Type | Spanner Type |
|----------|---------------|--------------|
| id       | INT64         | INT64        |
| measure1 | FLOAT64       | FLOAT64      |
 | measure2 | NUMERIC       | NUMERIC      |
| dim1     | BOOL          | BOOL         |
| dim2     | STRING        | STRING(MAX)  |


```shell
gcloud spanner databases ddl update ${SPANNER_DB} \
--instance=${SPANNER_INSTANCE} \
--ddl='CREATE TABLE demo_data ( id INT64, measure1 FLOAT64, measure2 NUMERIC, dim1 BOOL, dim2 STRING(MAX) ) PRIMARY KEY (id)'
```

create some fake data 

```shell
gcloud spanner rows insert \
  --instance=${SPANNER_INSTANCE} \
  --database=${SPANNER_DB} \
  --table=${SPANNER_TABLE} \
  --data=id=1,measure1=3.14,measure2=300,dim1=TRUE,dim2="label"
```

#### Example - part 4 of 4 - Run Scala Spark Job on Dataproc

create a dataproc cluster

```shell
gcloud dataproc clusters create ${CLUSTER_NAME} \
  --region us-central1 \
  --no-address \
  --master-machine-type n2-standard-4 \
  --master-boot-disk-type pd-balanced \
  --master-boot-disk-size 500 \
  --num-workers 2 \
  --worker-machine-type n2-standard-4 \
  --worker-boot-disk-type pd-balanced \
  --worker-boot-disk-size 500 \
  --image-version 2.2-debian12 \
  --project ${PROJECT_ID}
```

create a bucket to hold JARs
```shell
gcloud storage buckets create ${GCS_BUCKET} \
  --project=${PROJECT_ID} \
  --location=${GEO_REGION} \
  --uniform-bucket-level-access
```

Upload required JARs to Google Cloud Storage bucket

 * google-cloud-spanner-jdbc-2.17.1-single-jar-with-dependencies.jar

launch Scala Apache Spark job on Dataproc cluster

```shell
gcloud dataproc jobs submit spark --cluster ${CLUSTER_NAME} \
    --region=us-central1 \
    --jar=${GCS_BUCKET_JARS}/${APP_JAR_NAME} \
    --jars=${GCS_BUCKET_JARS}/google-cloud-spanner-jdbc-2.17.1-single-jar-with-dependencies.jar \
    -- ${PROJECT_ID} ${BQ_DATASET} ${BQ_TABLE} ${SPANNER_INSTANCE} ${SPANNER_DB} ${SPANNER_TABLE}
```

### Load testing of this template

TODO

