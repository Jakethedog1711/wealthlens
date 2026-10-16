-- Describe the raw price CSVs in S3 as a table. Creating it reads no data and costs nothing.
--
-- EXTERNAL       = Athena doesn't own the files; dropping the table never deletes them.
-- PARTITIONED BY = each date=YYYY-MM-DD/ folder is one partition, exposed as column dt.
-- projection.*   = "partition projection": Athena works out the folder names from the date
--                  range itself, so new days are found automatically (no MSCK REPAIR needed).
-- {bucket} is filled in by athena_query.py from .env.
CREATE EXTERNAL TABLE IF NOT EXISTS wealthlens.raw_prices (
    ticker       STRING,
    price_date   DATE,
    close_price  DECIMAL(19,4),
    currency     STRING
)
PARTITIONED BY (dt STRING)
ROW FORMAT DELIMITED FIELDS TERMINATED BY ','
LOCATION 's3://{bucket}/raw/prices/'
TBLPROPERTIES (
    'skip.header.line.count'      = '1',
    'projection.enabled'          = 'true',
    'projection.dt.type'          = 'date',
    'projection.dt.format'        = 'yyyy-MM-dd',
    'projection.dt.range'         = '2025-10-01,NOW',
    'projection.dt.interval'      = '1',
    'projection.dt.interval.unit' = 'DAYS',
    'storage.location.template'   = 's3://{bucket}/raw/prices/date=${dt}/'
)
