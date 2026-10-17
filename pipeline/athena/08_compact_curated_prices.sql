-- Compaction: rewrite ~250 tiny daily Parquet files into a few larger ones.
-- Every Parquet file carries its own metadata (schema, column statistics), so thousands of tiny
-- files waste reads; this is the "small files problem". CTAS (CREATE TABLE AS SELECT) makes
-- Athena read the small files once and write a compacted copy to a new S3 folder.
-- external_location must be an empty folder; to re-run, drop the table and delete that folder first.
CREATE TABLE wealthlens.curated_prices_compact
WITH (
    format            = 'PARQUET',
    write_compression = 'SNAPPY',
    external_location = 's3://{bucket}/curated/prices_compact/'
) AS
SELECT ticker, price_date, close_price, currency
FROM wealthlens.curated_prices
