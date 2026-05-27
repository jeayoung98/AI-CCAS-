# Retrieval Knowledge Import

This directory is for human-reviewed retrieval knowledge import files.

Do not place raw complaint text, audio files, unmasked personal information, API keys, or unreviewed generated content here.

## Data Rules

- `synthetic=false` is required for real import candidates.
- `reviewedBy` and `reviewedAt` are required for real import candidates.
- `OFFICIAL_GUIDE` items must come from reviewed official sources and the source must include `sourceOrganization`, `sourceUrl`, and `checkedAt`.
- `VERIFIED_CASE` items must be masked and reviewed by a person before import.
- `CATEGORY_REFERENCE` items must state whether they come from team policy or official/category policy through source metadata.
- `DRAFT`, `REVIEW_REQUIRED`, and `REJECTED` can be imported, but they are reported as non-searchable and search logic will not use them.
- Duplicate `(item_type, external_key, embedding_version)` imports fail. This importer does not overwrite, update, or re-embed existing items.

## Recommended Flow

1. Copy `retrieval-knowledge-template.json`.
2. Fill it with reviewed, masked data.
3. Run dry-run validation first.
4. Run actual import only after review.

Dry run:

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local --retrieval.import.enabled=true --retrieval.import.file-path=data/retrieval/import/reviewed-knowledge-v1.json --retrieval.import.allow-synthetic=false --retrieval.import.dry-run=true'
```

Actual import:

```powershell
$env:OPENAI_API_KEY="..."
.\gradlew.bat bootRun --args='--spring.profiles.active=local --retrieval.import.enabled=true --retrieval.import.file-path=data/retrieval/import/reviewed-knowledge-v1.json --retrieval.import.allow-synthetic=false --retrieval.import.dry-run=false'
```

Never import synthetic fixtures into a service database.
