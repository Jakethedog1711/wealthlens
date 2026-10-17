-- Curated layer: the same prices as raw_prices, but as typed, compressed Parquet written by
-- the prices_to_parquet Lambda. Same partition projection as the raw table.
CREATE EXTERNAL TABLE IF NOT EXISTS wealthlens.curated_prices (
    ticker       STRING,
    price_date   DATE,
    close_price  DECIMAL(19,4),
    currency     STRING
)
PARTITIONED BY (dt STRING)
STORED AS PARQUET
LOCATION 's3://{bucket}/curated/prices/'
TBLPROPERTIES (
    'projection.enabled'          = 'true',
    'projection.dt.type'          = 'date',
    'projection.dt.format'        = 'yyyy-MM-dd',
    'projection.dt.range'         = '2025-10-01,NOW',
    'projection.dt.interval'      = '1',
    'projection.dt.interval.unit' = 'DAYS',
    'storage.location.template'   = 's3://{bucket}/curated/prices/date=${dt}/'
)
