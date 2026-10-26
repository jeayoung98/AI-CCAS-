# Retrieval Evaluation

`POST /api/vector/retrieve` and the underlying retrieval services currently use `provisional-v1` thresholds. These values are temporary demo thresholds and must not be treated as validated production quality.

## Dataset Types

`src/test/resources/retrieval/evaluation/synthetic/` contains synthetic fixtures for code and metric verification only. They are not real complaint data, not official guidance, and not evidence of service accuracy.

`src/test/resources/retrieval/evaluation/human-reviewed/` is reserved for manually reviewed evaluation datasets. Do not store raw complaint text, audio files, or unmasked personal information there.

## Human-Reviewed Dataset Requirements

Each query should include:

- relevant `VERIFIED_CASE` item keys
- relevant `CATEGORY_REFERENCE` item keys
- relevant `OFFICIAL_GUIDE` item keys
- expected `EvidenceStatus`
- expected risk signal presence
- no-match expectation
- ambiguity expectation

Knowledge items should reference only reviewed, masked cases and verified official guide chunks.

## Metrics

The evaluation report calculates:

- channel `Recall@K`
- channel `Precision@K`
- channel `MRR`
- evidence status accuracy
- risk signal preservation rate
- no-match rejection accuracy
- ambiguity detection accuracy

Precision is averaged only for query/channel pairs with returned results. Recall and MRR are averaged only for query/channel pairs with at least one expected relevant item.

## Threshold Adjustment Flow

1. Add a human-reviewed dataset.
2. Run evaluation explicitly against an isolated evaluation database.
3. Review per-query failures and aggregate metrics.
4. Adjust thresholds in a separate task only after reviewed data supports the change.
5. Keep `thresholdProfileVersion` updated when threshold values change.

Synthetic fixture results must not be used to claim retrieval accuracy or tune production thresholds.

## Frozen retrieval comparison (metrics v2)

See [retrieval-optimization.md](retrieval-optimization.md) for commands, measured results and limitations.
`retrievalReplay` reads an already ingested isolated corpus and cached query embeddings. It does not
call OpenAI or ingest data. A repeatable-read transaction freezes the corpus across strategies;
SHA-256 fingerprints identify corpus and query inputs. Model/version mismatch, missing corpus
items, duplicate keys, wrong-channel relevance labels and unexpected eligible documents fail the run.

The original conditional `precisionAtK` field remains for compatibility. New `precisionAtFixedK`
uses unique relevant returned items divided by configured K and averages over **all** queries,
including empty results. Recall uses unique expected keys. Results beyond K are excluded and
duplicate returned keys cannot increase hit counts or move later results into K. Hit@1/3/5 and
MRR average over queries with at least one relevant item in the channel; `relevantQueryCount`
reports that denominator. Hit@5 is null for channels configured with K=3, not an extrapolated score.

The old `noMatchRejectionAccuracy` means refusal to claim reliable **cases**, and
`ambiguityDetectionAccuracy` is recall on positive ambiguity examples. The replay report also
includes TP/FP/FN/TN, precision and recall for ambiguity and for all-channel no-match (no relevant
item in any channel; correct rejection means INSUFFICIENT_EVIDENCE). Undefined precision/recall
is null. Do not conflate these definitions or compare legacy and fixed-K precision as an improvement.

Replay timings cover JDBC calls, result mapping and Java ranking for all three channels. They exclude
embedding, request validation, query/decision persistence, evidence evaluation and HTTP overhead.
EXPLAIN timings cover server execution separately. OpenAI API and actual API E2E timings need
their own real-embedding run; they are not inferred from replay latency.
