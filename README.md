## BigQuery to Spanner using Apache Spark in Scala

### Why create this repo ? 

 * Google Cloud customers need to move data from BigQuery to Spanner using Apache Spark in Scala
 * Dataproc [templates](https://github.com/GoogleCloudPlatform/dataproc-templates) cannot help as they are written in Python & Java
 * The Apache Spark SQL [connector](https://github.com/GoogleCloudDataproc/spark-spanner-connector) for Google Cloud Spanner only supports reads not writes 

### Why is this a hard problem ? 

**1. data type mapping**

The [spark-bigquery-connector](https://github.com/GoogleCloudDataproc/spark-bigquery-connector?tab=readme-ov-file#data-types) reads from BigQuery and converts BigQuery Google SQL Types into SparkSQL Types.

Apahce Spark JDBC is used to write to spanner, converting SparkSQL types into Spanner GoogleSQL types.

For some BigQuery GoogleSQL types, there is not equivalent in SparkSQL & Spanner GoogleSQL.

| BigQuery GoogleSQL Type   | SparkSQL Type | Spanner GoogleSQL Type |Notes |
|----------|---------------|--------------|--------------|
| [INT64](https://cloud.google.com/bigquery/docs/reference/standard-sql/data-types#integer_types)       |   LongType       | [INT64](https://cloud.google.com/spanner/docs/reference/standard-sql/data-types#integer_types)        |   |
| [FLOAT64](https://cloud.google.com/bigquery/docs/reference/standard-sql/data-types#floating_point_types) | DoubleType    | [FLOAT64](https://cloud.google.com/spanner/docs/reference/standard-sql/data-types#floating_point_types)      |  |
| [NUMERIC](https://cloud.google.com/bigquery/docs/reference/standard-sql/data-types#decimal_types) |   DecimalType     | [NUMERIC](https://cloud.google.com/spanner/docs/reference/standard-sql/data-types#decimal_types)      |  |
| [BIGNUMERIC](https://cloud.google.com/bigquery/docs/reference/standard-sql/data-types#decimal_types) |  DecimalType      | [NUMERIC](https://cloud.google.com/spanner/docs/reference/standard-sql/data-types#decimal_types)      | Spark & Spanner have no Big Numeric support  |
| [BOOL](https://cloud.google.com/bigquery/docs/reference/standard-sql/data-types#boolean_type)     |     BooleanType      | [BOOL](https://cloud.google.com/spanner/docs/reference/standard-sql/data-types#boolean_type)         |  |
| [STRING](https://cloud.google.com/bigquery/docs/reference/standard-sql/data-types#string_type)     |      StringType   | [STRING(MAX)](https://cloud.google.com/spanner/docs/reference/standard-sql/data-types#string_type)  |  |
| [DATE](https://cloud.google.com/bigquery/docs/reference/standard-sql/data-types#date_type)     |   DateType      |[DATE](https://cloud.google.com/spanner/docs/reference/standard-sql/data-types#date_type)  |  |
| [DATETIME](https://cloud.google.com/bigquery/docs/reference/standard-sql/data-types#datetime_type)     |   StringType      | [STRING(MAX)](https://cloud.google.com/spanner/docs/reference/standard-sql/data-types#string_type)  | Spark & Spanner have no DATETIME type|
| [TIME](https://cloud.google.com/bigquery/docs/reference/standard-sql/data-types#time_type)     |    LongType     | [INT64](https://cloud.google.com/spanner/docs/reference/standard-sql/data-types#integer_types)  | Spark & Spanner have no TIME type|
| [TIMESTAMP](https://cloud.google.com/bigquery/docs/reference/standard-sql/data-types#timestamp_type)     |   TimestampType      |[TIMESTAMP](https://cloud.google.com/spanner/docs/reference/standard-sql/data-types#timestamp_type)  |  |

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

1. [Dataproc2.2](https://cloud.google.com/dataproc/docs/concepts/versioning/dataproc-release-2.2) images are the target execution environment
2. Given dataproc image, need Java JDK 11 
3. Given dataproc image, need Scala 2.12.18
4. Also need sbt
5. Build a JAR of this code by running ```sbt package``` from root of this repo. We call this the ```APP_JAR```
6. upload JAR of this code to Google Cloud Storage
7. upload a JAR of the Spanner JDBC [driver](https://cloud.google.com/spanner/docs/jdbc-drivers) to Google Cloud Storage. We call this the ```SPANNER_JDBC_JAR```
8. If it does not exist, create a Spanner table with schema equal to source BigQuery table
9. Sumit a job to your dataproc cluster to move data from BigQuery to Spanner

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
| CLUSTER_NAME | The name of your Dataproc cluster       | my_cluster      |
| GCS_BUCKET_JARS | The Google Cloud Storage bucket for JARs       | gs://my-jar-bucket      |
| APP_JAR_NAME     | As above, the JAR of this template code          | spark-bigquery-spanner.jar         |
| SPANNER_JDBC_JAR     | As above, the JAR of Spanner JDBC driver          | google-cloud-spanner-jdbc-2.17.1-single-jar-with-dependencies.jar         |
| PROJECT_ID     | The ID of the GCP project with Datproc, BigQuery & Spanner        | ```PROJECT_ID=$(gcloud config list core/project --format="value(core.project)")```  |
| BQ_DATASET     | The BigQuery dataset with source data        | my_bq_dataset  |
| BQ_TABLE     | The BigQuery table with source data        | my_bq_table  |
| SPANNER_INSTANCE     | The Spanner instance with target table        | my_spanner_instance  |
| SPANNER_DB     | The Spanner database with target table        | my_spanner_db  |
| SPANNER_TABLE     | The target table in Spanner        | my_spanner_table  |

### Example of using this template

#### Example - part 1 of 4 - environment variables + setup

create some environment variables
```shell
export PROJECT_ID=$(gcloud config list core/project --format="value(core.project)")
export PROJECT_NUM=$(gcloud projects describe $PROJECT_ID --format="value(projectNumber)")
export GEO_REGION="US"
export GCS_BUCKET="gs://${PROJECT_ID}-sparkbigqueryspanner"
export GCS_BUCKET_JARS="${GCS_BUCKET}/jars"
export BQ_DATASET="sparkbigqueryspanner_demo_dataset"
export BQ_TABLE="demo"
export SPANNER_INSTANCE="sparkbigqueryspanner-demo-instance"
export SPANNER_DB="sparkbigqueryspanner-demo--db"
export SPANNER_TABLE="demo_data"
export CLUSTER_NAME="sparkbigqueryspanner-demo-cluster"
export APP_JAR_NAME="sparkbigqueryspanner.jar"
export SPANNER_JDBC_JAR="google-cloud-spanner-jdbc-2.17.1-single-jar-with-dependencies.jar"

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
 id:INT64,measure1:FLOAT64,measure2:NUMERIC,measure3:BIGNUMERIC,dim1:BOOL,dim2:STRING,dim3:DATE,dim4:DATETIME,dim5:TIME,dim6:TIMESTAMP
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
  CAST(1000 AS BIGNUMERIC) as measure3,
  FALSE AS dim1,
  "blabel" AS dim2,
  DATE(2024, 01, 01) as dim3,
  DATETIME(2008, 12, 25, 05, 30, 00) as dim4,
  TIME(15, 30, 00) as dim5,
  TIMESTAMP("2008-12-25 15:30:00+00") as dim6'
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

create a table in our Spanner DB: 
 *  column names matching BigQuery table
 *  column types as per mapping table above


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

 * ```APP_JAR```, the JAR of this code by running ```sbt package``` from root of this repo
 * ```SPANNER_JDBC_JAR```, the JAR of the Spanner JDBC [driver](https://cloud.google.com/spanner/docs/jdbc-drivers)

launch Scala Apache Spark job on Dataproc cluster

```shell
gcloud dataproc jobs submit spark --cluster ${CLUSTER_NAME} \
    --region=us-central1 \
    --jar=${GCS_BUCKET_JARS}/${APP_JAR_NAME} \
    --jars=${GCS_BUCKET_JARS}/${SPANNER_JDBC_JAR} \
    -- ${PROJECT_ID} ${BQ_DATASET} ${BQ_TABLE} ${SPANNER_INSTANCE} ${SPANNER_DB} ${SPANNER_TABLE}
```

### Load testing of this template

TODO

