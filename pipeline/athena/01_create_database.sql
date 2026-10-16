-- A "database" in Athena is just a named folder in the Glue Data Catalog for table definitions.
-- No data is stored here: the data stays in S3.
CREATE DATABASE IF NOT EXISTS wealthlens
