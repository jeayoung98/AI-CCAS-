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
